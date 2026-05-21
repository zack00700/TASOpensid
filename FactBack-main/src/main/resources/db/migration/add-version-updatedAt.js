db.getCollection('THIRDPARTY').updateMany(
  { version: { $exists: false } },
  [{ $set: { version: 0, updatedAt: new Date() } }]
);
