// Migration: add-invoice-sequences.js
// Purpose: Create INVOICE_SEQUENCE collection with defaults and fix draft invoices without draftNumber
// Run via: POST /admin/migration/sequences (SequenceMigrationService handles the Java side)
// This script is for reference / manual execution in mongo shell if needed.

const now = new Date();
const sequenceCol = db.getCollection("INVOICE_SEQUENCE");
const invoiceCol = db.getCollection("INVOICE");

// Create unique index
sequenceCol.createIndex({ sequenceId: 1 }, { unique: true });

// Insert default sequences if not present
function upsertSequence(sequenceId, prefix, nextValue, maximumDigits) {
  const existing = sequenceCol.findOne({ sequenceId: sequenceId });
  if (!existing) {
    sequenceCol.insertOne({
      sequenceId: sequenceId,
      prefix: prefix,
      nextValue: NumberLong(nextValue),
      maximumDigits: maximumDigits,
      invoiceTypeId: null,
      isDefault: true,
      createdAt: now,
      updatedAt: now
    });
    print("Created sequence: " + sequenceId);
  } else {
    print("Sequence already exists, skipping: " + sequenceId);
  }
}

upsertSequence("INVOICE_DRAFT", "DFT", 1, 5);
upsertSequence("INVOICE_FINAL", "INV", 1, 5);

// Fix DRAFT invoices without draftNumber
let legacyCounter = 1;
invoiceCol.find({
  status: "DRAFT",
  $or: [
    { draftNumber: { $exists: false } },
    { draftNumber: null },
    { draftNumber: "" }
  ]
}).sort({ createdDate: 1 }).forEach(function(inv) {
  const legacyNum = "DFT-LEGACY-" + String(legacyCounter).padStart(4, "0");
  invoiceCol.updateOne(
    { _id: inv._id },
    { $set: { draftNumber: legacyNum } }
  );
  legacyCounter++;
});
print("Draft invoices fixed: " + (legacyCounter - 1));

// Report finals with UUID (preserved as-is)
const legacyFinals = invoiceCol.countDocuments({
  status: "FINAL",
  finalNumber: { $regex: /^[0-9a-f]{8}-[0-9a-f]{4}/ }
});
print("Finals with UUID finalNumber (legacy, preserved): " + legacyFinals);
print("Migration script complete.");
