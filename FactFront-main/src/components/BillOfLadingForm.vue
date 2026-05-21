<!-- src/components/BillOfLadingForm.vue -->
<script setup lang="ts">
import { ref, watch, computed, onMounted } from "vue";
import { useI18n } from "vue-i18n";
const { t } = useI18n();
import { useIsoCode } from "../composables/use.iso-code";
import {
  X,
  Plus,
  Trash2,
  FileText,
  Loader2,
  ChevronDown,
  ChevronUp,
  Copy,
  Save,
  ArrowLeft,
  Package,
  Truck,
  Ship,
  Train,
  AlertCircle,
  Search,
} from "lucide-vue-next";
import { v4 as uuidv4 } from "uuid";
import invoiceService from "../services/invoiceService";
import billOfLadingService, { type VesselSnapshot } from "../services/billOfLadingService";
import vesselVisitService, { type VesselVisit, sanitizeVesselQuery } from "../services/vesselVisitService";
import itemService from "../services/itemService";
import InvoicePreview from "./InvoicePreview.vue";
import ThirdPartyAutocomplete from "./ui/ThirdPartyAutocomplete.vue";
import type { ThirdParty } from '../types/third-party';
import Select from "./ui/Select.vue";
import Input from "./ui/Input.vue";

/* ----------------------------- Date Utilities ------------------------------ */

/**
 * Convert a date input value (YYYY-MM-DD) to ISO 8601 format for MongoDB ISODate
 * @param dateString - Date string in YYYY-MM-DD format
 * @returns ISO 8601 string that Jackson can convert to Date, or null if invalid
 */
function dateInputToISO(dateString: string): string | null {
  if (!dateString || !dateString.trim()) return null;
  
  try {
    // Create date at midnight UTC to ensure consistent timezone handling
    const date = new Date(dateString + 'T00:00:00.000Z');
    if (isNaN(date.getTime())) return null;
    
    // Return ISO string that Jackson can parse into Java Date -> MongoDB ISODate
    return date.toISOString();
  } catch (error) {
    console.warn('Invalid date string:', dateString);
    return null;
  }
}

/**
 * Convert MongoDB ISODate or ISO string to date input format (YYYY-MM-DD)
 * @param isoValue - ISO string, Date object, or MongoDB ISODate
 * @returns Date string in YYYY-MM-DD format, or empty string if invalid
 */
function isoToDateInput(isoValue: string | Date | null | undefined): string {
  if (!isoValue) return '';
  
  try {
    let date: Date;
    
    if (isoValue instanceof Date) {
      date = isoValue;
    } else if (typeof isoValue === 'string') {
      date = new Date(isoValue);
    } else {
      return '';
    }
    
    if (isNaN(date.getTime())) return '';
    
    return date.toISOString().split('T')[0];
  } catch (error) {
    console.warn('Invalid ISO value:', isoValue);
    return '';
  }
}

/**
 * Add one year to a date and return in date input format
 * @param dateString - Date string in YYYY-MM-DD format
 * @returns Date string one year later in YYYY-MM-DD format
 */
function addOneYearToDateInput(dateString: string): string {
  if (!dateString) return '';
  
  try {
    const date = new Date(dateString + 'T00:00:00.000Z');
    if (isNaN(date.getTime())) return '';
    
    date.setFullYear(date.getFullYear() + 1);
    return date.toISOString().split('T')[0];
  } catch (error) {
    console.warn('Error adding year to date:', dateString);
    return '';
  }
}

/* ----------------------------- Types/Interfaces ---------------------------- */

interface Commodity {
  description: string;
  weightKg: number;
  volumeM3: number;
  packagesNumber: number;
  hazardous: boolean;
  hazardClass?: string | null;
  unNumber?: string | null;
}

type ItemType = "container" | "breakbulk" | "vehicle";

interface Item {
  id?: string;
  clientId: string;
  itemType: ItemType;
  type: string;
  itemNumber: string;
  status: string;
  ownerId: string;
  position: string;
  lastInspection: string;
  nextInspection: string;
  notes: string;
  weightKg: number;
  volumeM3: number;
  itemStatus?: string | null;
  lifeCycles?: string[];
  relatedInvoice?: string | null;
  billOfLadingId?: string | null;
  expanded?: boolean;
  errors?: Record<string, string>;
}

interface BillOfLading {
  blNumber: string;
  status: "Draft" | "Final" | "Cancelled";
  shipper: string;
  consignee: string;
  notifyParty: { name: string; address: string };
  transportType: "Vessel" | "Train" | "Truck";
  vessel: string;
  voyage: string;
  portOfLoading: string;
  portOfDischarge: string;
  placeOfDelivery: string;
  driver: string;
  trainNumber: string;
  truckNumber: string;
  commodity: Commodity;
  items: Item[];
  transportSnapshot: VesselSnapshot | null;
  // Extended fields
  bookingNumber?: string;
  shippingLine?: string;
  incoterms?: string;
  freightPayableAt?: string;
  bolDate?: string;
  houseBolNumber?: string;
  masterBolNumber?: string;
}

/* ------------------------------ Constants ---------------------------- */

const CATEGORY_SET = new Set(["container", "breakbulk", "vehicle"]);
const TITLE_ITEM_TYPE: Record<ItemType, string> = {
  container: "Container",
  breakbulk: "Breakbulk",
  vehicle: "Vehicle",
};

const TRANSPORT_ICONS = {
  Vessel: Ship,
  Train: Train,
  Truck: Truck,
};

/* --------------------------------- Helpers --------------------------------- */

function isUuid(v: any): boolean {
  return typeof v === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
}
function ensureUuid(id?: string): string {
  return isUuid(id) ? id : uuidv4();
}

function isMongoObjectIdString(v: any): boolean {
  // Classic 24-hex ObjectId string
  return typeof v === "string" && /^[0-9a-f]{24}$/i.test(v);
}

/**
 * Extract a string id from various shapes:
 * - "abc-uuid-..." (string)
 * - { $oid: "abcdef..." }
 * - { oid: "abcdef..." }
 * - _id as above
 */
function extractStringId(src: any): string | undefined {
  if (!src) return undefined;

  // If we received _id and no id, use it as source
  const val = src.id ?? src._id ?? src;

  if (typeof val === "string") return val;
  if (typeof val === "object" && val !== null) {
    if (typeof val.$oid === "string") return val.$oid;
    if (typeof val.oid === "string") return val.oid;
  }
  return undefined;
}

const createEmptyForm = (): BillOfLading => ({
  blNumber: "",
  status: "Draft",
  shipper: "",
  consignee: "",
  notifyParty: { name: '', address: '' },
  transportType: "Vessel",
  vessel: "",
  voyage: "",
  portOfLoading: "",
  portOfDischarge: "",
  placeOfDelivery: "",
  driver: "",
  trainNumber: "",
  truckNumber: "",
  commodity: {
    description: "",
    weightKg: 0,
    volumeM3: 0,
    packagesNumber: 0,
    hazardous: false,
    hazardClass: null,
    unNumber: null,
  },
  items: [],
  transportSnapshot: null,
  bookingNumber: "",
  shippingLine: "",
  incoterms: "",
  freightPayableAt: "",
  bolDate: "",
  houseBolNumber: "",
  masterBolNumber: "",
});

function toUiCategory(val: any): ItemType {
  const low = String(val ?? "").toLowerCase();
  return (CATEGORY_SET.has(low) ? low : "container") as ItemType;
}

function maybeSubtype(val: any): string {
  const s = String(val ?? "").trim();
  return CATEGORY_SET.has(s.toLowerCase()) ? "" : s;
}

function mapApiItemToItem(api: any): Item {
  const uiItemType = toUiCategory(api.itemType);
  const subType = api.subType ?? maybeSubtype(api.type);

  const parseNum = (n: any) => {
    const v = Number(n);
    return Number.isFinite(v) ? v : 0;
  };

  // Normalize id from multiple shapes
  const rawId = extractStringId(api);
  const idStr = typeof rawId === "string" ? rawId : undefined;

  return {
    clientId: idStr ?? uuidv4(),                    // UI key
    id: idStr,                                       // keep whatever came, as string (might be ObjectId or UUID)
    itemType: uiItemType,
    type: subType,
    itemNumber: api.itemNumber ?? "",
    status: api.status ?? api.itemStatus ?? "Pending",
    ownerId: api.ownerId != null ? String(api.ownerId) : "",
    position: api.position ?? "",
    // Convert MongoDB ISODate/ISO strings to date input format for display
    lastInspection: isoToDateInput(api.lastInspectionDate ?? api.lastInspection),
    nextInspection: isoToDateInput(api.nextInspectionDate ?? api.nextInspection),
    notes: api.notes ?? "",
    weightKg: parseNum(api.weightKg ?? api.weight),
    volumeM3: parseNum(api.volumeM3 ?? api.volume),
    itemStatus: api.itemStatus ?? null,
    lifeCycles: Array.isArray(api.lifeCycles) ? api.lifeCycles : [],
    relatedInvoice: api.relatedInvoice ?? null,
    billOfLadingId: api.billOfLadingId ?? null,
    expanded: false,
    errors: {},
  };
}

// toApiItem(it) - Convert UI item to API format with ISO strings for MongoDB ISODate
function toApiItem(it: Item) {
  const w = Number(it.weightKg);
  const v = Number(it.volumeM3);
  const outId = ensureUuid(it.id || it.clientId);

  const item = {
    id: outId,
    itemType: TITLE_ITEM_TYPE[it.itemType] || "Container",
    itemNumber: (it.itemNumber ?? "").trim(),
    type: (it.type ?? "").trim(),
    ownerId: (it.ownerId ?? "").trim() || null,
    position: (it.position ?? "").trim() || null,
    itemStatus: it.itemStatus ?? null,
    // Convert date input format to ISO 8601 strings (Jackson converts to Date → MongoDB ISODate)
    lastInspectionDate: dateInputToISO(it.lastInspection),
    nextInspectionDate: dateInputToISO(it.nextInspection),
    notes: (it.notes ?? "").trim() || null,
    status: it.status || null,
    lifeCycles: Array.isArray(it.lifeCycles) ? it.lifeCycles : [],
    relatedInvoice: it.relatedInvoice ?? null,
    billOfLadingId: it.billOfLadingId ?? null,
    weight: Number.isFinite(w) ? w : 0,
    volume: Number.isFinite(v) ? v : 0,
  };

  // Only add additionalProperties if it has content
  return item;
}

function toApiCommodity(c: Commodity) {
  const commodity = {
    description: c.description ?? "",
    weightKg: Number(c.weightKg ?? 0),
    volumeM3: Number(c.volumeM3 ?? 0),
    packagesNumber: Number(c.packagesNumber ?? 0),
    isHazardous: !!c.hazardous,
    hazardClass_: c.hazardClass ?? null,
    unNumber: c.unNumber ?? null,
  };
  
  // Only add additionalProperties if it has content
  return commodity;
}

// Only send vesselVisitId for vessel transports
function createTransportPayload(formData: BillOfLading) {
  const transport: any = {
    type: formData.transportType.toUpperCase(),
  };

  if (formData.transportType === "Vessel") {
    const vvId = formData.transportSnapshot?.id || selectedVesselId.value || null;
    if (vvId) transport.vesselVisitId = vvId;
    // Only include vessel object, not empty train/truck
    transport.vessel = formData.transportSnapshot || {};
  } else if (formData.transportType === "Train") {
    transport.trainServiceId = formData.trainNumber || null;
    // Only include train object, not empty vessel/truck
    transport.train = {
      trainNumber: formData.trainNumber || "",
      driver: formData.driver || "",
    };
  } else if (formData.transportType === "Truck") {
    transport.truckTripId = formData.truckNumber || null;
    // Only include truck object, not empty vessel/train
    transport.truck = {
      truckNumber: formData.truckNumber || "",
      driver: formData.driver || "",
    };
  }

  return transport;
}

const createCleanPayload = (basePayload: any) => {
  const cleanPayload = { ...basePayload };
  
  // Remove additionalProperties if empty or null
  if (!cleanPayload.additionalProperties || Object.keys(cleanPayload.additionalProperties).length === 0) {
    delete cleanPayload.additionalProperties;
  }
  
  return cleanPayload;
};

/* ------------------------- Diff helpers ------------------------ */

type Patch = Record<string, any> & { id: string };

const normalizeForCompare = (it: Item) => {
  const num = (x: any) => (Number.isFinite(Number(x)) ? Number(x) : 0);
  return {
    id: it.id || null,
    itemNumber: (it.itemNumber || "").trim(),
    itemType: TITLE_ITEM_TYPE[it.itemType],
    type: (it.type || "").trim(),
    ownerId: (it.ownerId || "").trim(),
    position: (it.position || "").trim(),
    // Convert to ISO strings for comparison
    lastInspectionDate: dateInputToISO(it.lastInspection),
    nextInspectionDate: dateInputToISO(it.nextInspection),
    notes: it.notes || "",
    status: it.status || null,
    weight: num(it.weightKg),
    volume: num(it.volumeM3),
    lifeCycles: Array.isArray(it.lifeCycles) ? it.lifeCycles : [],
    relatedInvoice: it.relatedInvoice ?? null,
    billOfLadingId: it.billOfLadingId ?? null,
  };
};

function computeChanges(
  originalApiItems: any[] | null | undefined,
  currentUiItems: Item[]
) {
  // Normalize originals (snapshot from backend at load time)
  const originalNorm = (originalApiItems ?? []).map((x) =>
    normalizeForCompare(mapApiItemToItem(x))
  );

  // Index ALL originals that have an id
  const originalById = new Map<string, any>(
    originalNorm
      .filter((x) => !!x.id)
      .map((x) => [x.id as string, x])
  );

  // Normalize current UI items and ensure they carry an id (fallback to clientId for comparison)
  const currentNorm = (currentUiItems ?? []).map((x) => {
    const n = normalizeForCompare(x);
    n.id = (x.id || x.clientId) as string | null;
    return n;
  });

  const currentById = new Map<string, any>(
    currentNorm
      .filter((x) => !!x.id)
      .map((x) => [x.id as string, x])
  );

  // NEW = in current but NOT in original
  const newItems = currentNorm
    .filter((n) => !n.id || !originalById.has(n.id as string))
    .map((n) => ({
      ...n,
      id: ensureUuid(typeof n.id === "string" ? n.id : undefined), // ensure UUID id for backend
    }));

  // UPDATED = present in both sets and any field differs
  const updatedItems: Patch[] = [];
  for (const [id, now] of currentById.entries()) {
    const was = originalById.get(id);
    if (!was) continue;

    const patch: Patch = { id };
    let changed = false;

    for (const k of Object.keys(now)) {
      if (k === "id") continue;
      const a = JSON.stringify(now[k]);
      const b = JSON.stringify(was[k]);
      if (a !== b) {
        (patch as any)[k] = now[k];
        changed = true;
      }
    }

    if (changed) updatedItems.push(patch);
  }

  // REMOVED = in original but NOT in current
  const removedItemIds = [...originalById.keys()].filter(
    (id) => !currentById.has(id)
  );

  return { newItems, updatedItems, removedItemIds };
}

/* --------------------------------- Props/IO -------------------------------- */

const props = defineProps<{
  editMode?: boolean;
  initialData?: BillOfLading & {
    id?: string;
    items?: any[] | null;
    itemIds?: string[] | null;
    transport?: any;
  };
}>();

const emit = defineEmits<{
  (e: "submit", data: any): void;
  (e: "cancel"): void;
}>();

/* --------------------------------- State ----------------------------------- */

const formData = ref<BillOfLading>(createEmptyForm());
const blId = ref<string | null>(null);

const { isoCodes, getAll: getAllIsoCodes } = useIsoCode();
const activeIsoCodes = computed(() =>
  isoCodes.value
    .filter(c => c.isActive)
    .sort((a, b) => a.code.localeCompare(b.code))
);
onMounted(() => { getAllIsoCodes(false); });

const isGeneratingInvoice = ref(false);
const invoicePreview = ref<{ id: string; url: string } | null>(null);
const vesselQuery = ref("");
const vesselSearchResults = ref<VesselVisit[]>([]);
const isRefreshingTransport = ref(false);
const refreshDisabled = ref(false);
const activeStep = ref<'basic' | 'parties' | 'transport' | 'commodity' | 'items'>('basic');
const isSubmitting = ref(false);
let searchTimer: number | undefined;

const suppressNextVesselSearch = ref(false);
const selectedVesselId = ref<string | null>(null);

// Stable copy to avoid accidental deletions on diff
const originalApiItems = ref<any[]>([]);

const errors = ref<Record<string, string>>({});

/* -------------------------- Progress Steps ------------------------- */

const steps = computed(() => [
  { id: 'basic', label: t("billOfLadingForm.step.basicInfo"), icon: FileText },
  { id: 'parties', label: t("billOfLadingForm.step.parties"), icon: Package },
  { id: 'transport', label: t("billOfLadingForm.step.transport"), icon: Ship },
  { id: 'commodity', label: t("billOfLadingForm.step.commodity"), icon: Package },
  { id: 'items', label: t("billOfLadingForm.step.items"), icon: Package },
]);

const currentStepIndex = computed(() => steps.value.findIndex(s => s.id === activeStep.value));

/* -------------------------- Derived commodity sums ------------------------- */

const totalWeightKg = computed(() =>
  (formData.value.items ?? []).reduce(
    (sum, it) => sum + (Number(it.weightKg) || 0),
    0
  )
);

const totalVolumeM3 = computed(() =>
  (formData.value.items ?? []).reduce(
    (sum, it) => sum + (Number(it.volumeM3) || 0),
    0
  )
);

watch([totalWeightKg, totalVolumeM3], ([w, v]) => {
  formData.value.commodity.weightKg = w;
  formData.value.commodity.volumeM3 = v;
});

/* --------------------------------- Watchers -------------------------------- */

watch(
  () => props.initialData,
  async (newVal) => {
    if (newVal) {
      const { id, items, itemIds, commodity, transport, ...rest } = newVal as any;
      blId.value = id || null;

      formData.value = JSON.parse(JSON.stringify({ ...createEmptyForm(), ...rest }));

      // Convert legacy string notifyParty (from API) to { name, address } object
      const loadedNotify = (newVal as any).notifyParty;
      formData.value.notifyParty =
        typeof loadedNotify === 'string'
          ? (() => {
              const idx = loadedNotify.indexOf('\n');
              return idx === -1
                ? { name: loadedNotify, address: '' }
                : { name: loadedNotify.slice(0, idx), address: loadedNotify.slice(idx + 1) };
            })()
          : (loadedNotify ?? { name: '', address: '' });

      if (commodity) {
        formData.value.commodity = {
          description: commodity.description ?? "",
          weightKg: Number(commodity.weightKg ?? 0),
          volumeM3: Number(commodity.volumeM3 ?? 0),
          packagesNumber: Number(commodity.packagesNumber ?? 0),
          hazardous: commodity.hazardous ?? commodity.isHazardous ?? false,
          hazardClass: commodity.hazardClass ?? commodity.hazardClass_ ?? null,
          unNumber: commodity.unNumber ?? null,
        };
      }

      let rawItems: any[] = [];
      if (Array.isArray(items)) {
        rawItems = items;
      } else if (Array.isArray(itemIds) && itemIds.length) {
        try {
          rawItems = await itemService.fetchMany(itemIds);
        } catch (e) {
          console.warn("Failed to load items by IDs", e);
          rawItems = [];
        }
      }
      formData.value.items = (rawItems ?? []).map(mapApiItemToItem);
      originalApiItems.value = JSON.parse(JSON.stringify(rawItems ?? []));

      // Initialize transport snapshot / query nicely (no "N/A" or parentheses in input)
      if (transport?.vesselVisitId) {
        selectedVesselId.value = transport.vesselVisitId;
        // Prefer snapshot from backend if provided
        if (transport.vessel) {
          formData.value.transportSnapshot = {
            id: transport.vesselVisitId,
            vesselName: transport.vessel.vesselName || "",
            imo: transport.vessel.imo || "",
            callSign: transport.vessel.callSign || "",
            voyageIn: transport.vessel.voyageIn || "",
            voyageOut: transport.vessel.voyageOut || "",
            operator: transport.vessel.operator || "",
            port: transport.vessel.port || "",
            terminal: transport.vessel.terminal || "",
            berth: transport.vessel.berth || "",
            eta: transport.vessel.eta || null,
            etd: transport.vessel.etd || null,
            ata: transport.vessel.ata || null,
            atd: transport.vessel.atd || null,
          };
        }
        suppressNextVesselSearch.value = true;
        vesselQuery.value = formData.value.transportSnapshot?.vesselName || formData.value.vessel || "";
      } else {
        vesselQuery.value = "";
        formData.value.transportSnapshot = null;
      }

      refreshDisabled.value = false;
    } else {
      formData.value = createEmptyForm();
      blId.value = null;
      vesselQuery.value = "";
      refreshDisabled.value = false;
      originalApiItems.value = [];
    }
  },
  { immediate: true }
);

// Robust, sanitized, debounced search watcher
watch(vesselQuery, (newVal) => {
  if (suppressNextVesselSearch.value) {
    suppressNextVesselSearch.value = false;
    return;
  }
  const q = sanitizeVesselQuery(newVal || "");
  if (!q || q.length < 2) {
    vesselSearchResults.value = [];
    return;
  }
  clearTimeout(searchTimer);
  searchTimer = window.setTimeout(async () => {
    try {
      vesselSearchResults.value = await vesselVisitService.search(q);
    } catch (err) {
      console.warn("Vessel search failed for:", q, err);
      vesselSearchResults.value = [];
    }
  }, 300);
});

watch(
  () => formData.value.transportType,
  async (type) => {
    if (type === "Vessel") {
      formData.value.driver = "";
      formData.value.trainNumber = "";
      formData.value.truckNumber = "";
    } else if (type === "Train") {
      formData.value.vessel = "";
      formData.value.voyage = "";
      formData.value.portOfLoading = "";
      formData.value.portOfDischarge = "";
      formData.value.placeOfDelivery = "";
      formData.value.truckNumber = "";
      formData.value.transportSnapshot = null;
      selectedVesselId.value = null;
      vesselQuery.value = "";
    } else if (type === "Truck") {
      formData.value.vessel = "";
      formData.value.voyage = "";
      formData.value.portOfLoading = "";
      formData.value.portOfDischarge = "";
      formData.value.placeOfDelivery = "";
      formData.value.trainNumber = "";
      formData.value.transportSnapshot = null;
      selectedVesselId.value = null;
      vesselQuery.value = "";
    }
    if (blId.value && formData.value.status !== "Final") {
      try {
        await billOfLadingService.updateTransport(blId.value, {
          type: type.toUpperCase() as any,
        });
      } catch (e) {
        console.error(e);
      }
    }
  }
);

/* ------------------------------- UI Handlers ------------------------------- */

const selectVesselVisit = async (visit: VesselVisit) => {
  suppressNextVesselSearch.value = true;
  selectedVesselId.value = visit.id;

  // Display only vessel name to avoid poisoning search input
  vesselQuery.value = visit.vesselName || "";
  vesselSearchResults.value = [];

  const snapshot = billOfLadingService.mapVisitToSnapshot(visit);
  formData.value.transportSnapshot = snapshot;

  if (blId.value && formData.value.status !== "Final") {
    try {
      await billOfLadingService.updateTransport(blId.value, {
        type: "VESSEL",
        vesselVisitId: visit.id,
      });
      refreshDisabled.value = false;
    } catch (e) {
      console.error("Failed to update transport with vessel visit:", e);
      refreshDisabled.value = false;
    }
  }
};

const refreshSnapshot = async () => {
  if (!blId.value) return;
  try {
    isRefreshingTransport.value = true;
    const snapshot = await billOfLadingService.refreshTransport(blId.value);
    formData.value.transportSnapshot = snapshot;
    refreshDisabled.value = false;
  } catch (e) {
    console.warn("Failed to refresh vessel snapshot", e);
    refreshDisabled.value = true;
  } finally {
    isRefreshingTransport.value = false;
  }
};

const getInputClasses = (fieldName: string, variant: 'default' | 'sm' = 'default') => {
  const baseClasses = variant === 'sm' 
    ? "block w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200"
    : "block w-full px-4 py-3 border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200";
  
  return {
    [baseClasses]: true,
    "border-red-300 bg-red-50": errors.value[fieldName],
  };
};

const getItemInputClasses = (item: Item, field: string) => {
  return {
    "block w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all duration-200": true,
    "border-red-300 bg-red-50": item.errors && item.errors[field],
  };
};

const toggleItem = (item: Item) => {
  item.expanded = !item.expanded;
};

// Updated to work with date input format
function onNotifyPartySelect(tp: ThirdParty) {
  formData.value.notifyParty.address = tp.companyAddress ?? '';
}

const handleLastInspectionChange = (item: Item) => {
  if (item.lastInspection) {
    // Add one year to the last inspection date and keep in date input format
    const nextInspectionDate = addOneYearToDateInput(item.lastInspection);
    item.nextInspection = nextInspectionDate;
  } else {
    item.nextInspection = "";
  }
};

const addItem = () => {
  const newId = uuidv4();
  formData.value.items.push({
    clientId: newId,
    id: newId,              // <-- ensure it exists now
    itemType: "container",
    type: "",
    itemNumber: "",
    status: "Pending",
    ownerId: "",
    position: "",
    lastInspection: "",
    nextInspection: "",
    notes: "",
    weightKg: 0,
    volumeM3: 0,
    lifeCycles: [],
    relatedInvoice: null,
    billOfLadingId: null,
    expanded: true,
    errors: {},
  });
};

const removeItem = (index: number) => {
  formData.value.items.splice(index, 1);
};

const duplicateItem = (index: number) => {
  const item = formData.value.items[index];
  const copy = JSON.parse(JSON.stringify(item)) as Item;

  // generate one UUID for both id and clientId
  const newId = uuidv4();

  copy.id = newId;
  copy.clientId = newId;
  copy.itemNumber = "";       // reset number for the duplicate
  copy.expanded = true;       // open the form for editing

  formData.value.items.splice(index + 1, 0, copy);
};

const saveItem = (item: Item) => {
  item.errors = {};
  item.itemNumber = item.itemNumber?.trim();
  item.ownerId = item.ownerId?.trim();
  item.type = item.type?.trim();

  if (!item.itemNumber) item.errors.itemNumber = t("billOfLadingForm.error.itemNumberRequired");
  if (!item.ownerId) item.errors.ownerId = t("billOfLadingForm.error.ownerIdRequired");
  if (item.itemType === "container" && !item.type) {
    item.errors.type = t("billOfLadingForm.error.containerSubtypeRequired");
  }

  if (item.lastInspection && item.nextInspection) {
    const last = new Date(item.lastInspection + 'T00:00:00');
    const next = new Date(item.nextInspection + 'T00:00:00');
    if (next <= last) {
      item.errors.nextInspection = t("billOfLadingForm.error.nextInspectionAfterLast");
    }
  }
  item.expanded = Object.keys(item.errors).length > 0;
};

const nextStep = () => {
  if (currentStepIndex.value < steps.value.length - 1) {
    activeStep.value = steps.value[currentStepIndex.value + 1].id as any;
  }
};

const prevStep = () => {
  if (currentStepIndex.value > 0) {
    activeStep.value = steps.value[currentStepIndex.value - 1].id as any;
  }
};

const goToStep = (stepId: string) => {
  activeStep.value = stepId as any;
};

/* -------------------------------- Validation ------------------------------- */

const validateForm = () => {
  errors.value = {};
  let isValid = true;

  if (!formData.value.blNumber) {
    errors.value.blNumber = t("billOfLadingForm.error.blNumberRequired");
    isValid = false;
  }
  if (!formData.value.shipper) {
    errors.value.shipper = t("billOfLadingForm.error.shipperRequired");
    isValid = false;
  }
  if (!formData.value.consignee) {
    errors.value.consignee = t("billOfLadingForm.error.consigneeRequired");
    isValid = false;
  }

  if (formData.value.transportType === "Vessel") {
    if (!formData.value.transportSnapshot?.id) {
      errors.value.vessel = t("billOfLadingForm.error.vesselVisitRequired");
      isValid = false;
    }
  } else if (formData.value.transportType === "Train") {
    if (!formData.value.driver) {
      errors.value.driver = t("billOfLadingForm.error.driverRequired");
      isValid = false;
    }
    if (!formData.value.trainNumber) {
      errors.value.trainNumber = t("billOfLadingForm.error.trainNumberRequired");
      isValid = false;
    }
  } else if (formData.value.transportType === "Truck") {
    if (!formData.value.driver) {
      errors.value.driver = t("billOfLadingForm.error.driverRequired");
      isValid = false;
    }
    if (!formData.value.truckNumber) {
      errors.value.truckNumber = t("billOfLadingForm.error.truckNumberRequired");
      isValid = false;
    }
  }

  if (!formData.value.commodity.description) {
    errors.value["commodity.description"] = t("billOfLadingForm.error.commodityDescriptionRequired");
    isValid = false;
  }
  if ((formData.value.commodity.packagesNumber ?? 0) <= 0) {
    errors.value["commodity.packagesNumber"] = t("billOfLadingForm.error.packagesNumberPositive");
    isValid = false;
  }

  return isValid;
};

/* --------------------------------- Submit ---------------------------------- */

// Updated handleSubmit function in BillOfLadingForm.vue

const handleSubmit = async () => {
  isSubmitting.value = true;
  
  try {
    const isFormValid = validateForm();

    formData.value.items.forEach((item) => saveItem(item));
    const hasInvalidItems = formData.value.items.some(
      (item) => item.errors && Object.keys(item.errors).length > 0
    );
    if (hasInvalidItems) {
      errors.value.items = t("billOfLadingForm.error.fixItemErrors");
    }
    if (!isFormValid || hasInvalidItems) {
      activeStep.value = 'basic';
      return;
    }

    // Create
    if (!props.editMode || !blId.value) {
      const normalizedItems = formData.value.items.map(toApiItem);
      const transport = createTransportPayload(formData.value);
      
      const basePayload = {
        blNumber: formData.value.blNumber,
        blType: "Original",
        status: formData.value.status,
        shipper: formData.value.shipper,
        consignee: formData.value.consignee,
        notifyParty:
          formData.value.notifyParty?.name || formData.value.notifyParty?.address
            ? `${formData.value.notifyParty.name}\n${formData.value.notifyParty.address}`
            : null,
        vessel: formData.value.vessel || null,
        voyage: formData.value.voyage || null,
        portOfLoading: formData.value.portOfLoading || null,
        portOfDischarge: formData.value.portOfDischarge || null,
        placeOfDelivery: formData.value.placeOfDelivery || null,
        transportType: formData.value.transportType,
        driver: formData.value.driver || null,
        trainNumber: formData.value.trainNumber || null,
        truckNumber: formData.value.truckNumber || null,
        commodity: toApiCommodity(formData.value.commodity),
        items: normalizedItems,
        transport,
      };

      const payload = createCleanPayload(basePayload);
      emit("submit", payload);
      return;
    }

    // ✅ IMPROVED: Edit using complete PUT approach
    try {
      
      // Check if anything actually changed
      const initialNotify = props.initialData?.notifyParty;
      const initialNotifyStr =
        typeof initialNotify === 'string'
          ? initialNotify
          : `${initialNotify?.name ?? ''}\n${initialNotify?.address ?? ''}`.trim();
      const currentNotifyStr =
        `${formData.value.notifyParty.name}\n${formData.value.notifyParty.address}`.trim();
      const notifyPartyDirty = initialNotifyStr !== currentNotifyStr;

      const hasMainFieldChanges = props.initialData ? (
        formData.value.blNumber !== props.initialData.blNumber ||
        formData.value.status !== props.initialData.status ||
        formData.value.shipper !== props.initialData.shipper ||
        formData.value.consignee !== props.initialData.consignee ||
        notifyPartyDirty ||
        formData.value.transportType !== props.initialData.transportType ||
        formData.value.driver !== (props.initialData.driver || "") ||
        formData.value.trainNumber !== (props.initialData.trainNumber || "") ||
        formData.value.bookingNumber !== (props.initialData.bookingNumber || "") ||
        formData.value.shippingLine !== (props.initialData.shippingLine || "") ||
        formData.value.incoterms !== (props.initialData.incoterms || "") ||
        formData.value.freightPayableAt !== (props.initialData.freightPayableAt || "") ||
        formData.value.bolDate !== isoToDateInput(props.initialData.bolDate) ||
        formData.value.houseBolNumber !== (props.initialData.houseBolNumber || "") ||
        formData.value.masterBolNumber !== (props.initialData.masterBolNumber || "") ||
        formData.value.truckNumber !== (props.initialData.truckNumber || "") ||
        !commodityEqual(formData.value.commodity, props.initialData.commodity)
      ) : false;

      // Check if items array changed (simple length and content check)
      const hasItemChanges = !props.initialData?.items || 
        formData.value.items.length !== props.initialData.items.length ||
        formData.value.items.some((item, index) => {
          const originalItem = props.initialData?.items?.[index];
          if (!originalItem) return true;
          
          return item.itemNumber !== originalItem.itemNumber ||
            item.itemType !== originalItem.itemType ||
            item.type !== originalItem.type ||
            item.ownerId !== originalItem.ownerId ||
            item.position !== originalItem.position ||
            item.status !== originalItem.status ||
            item.lastInspection !== isoToDateInput(originalItem.lastInspectionDate) ||
            item.nextInspection !== isoToDateInput(originalItem.nextInspectionDate) ||
            item.notes !== (originalItem.notes || "") ||
            item.weightKg !== (originalItem.weight || 0) ||
            item.volumeM3 !== (originalItem.volume || 0);
        });

      const initialVV = props.initialData?.transport?.vesselVisitId || null;
      const currentVV = formData.value.transportSnapshot?.id || null;
      const hasTransportChanges = initialVV !== currentVV;

      const hasAnyChanges = hasItemChanges || hasMainFieldChanges || hasTransportChanges;

      if (!hasAnyChanges) {
        emit("submit", {});
        return;
      }

      // ✅ SIMPLIFIED: Send complete payload with items in single PUT request
      const normalizedItems = formData.value.items.map(toApiItem);
      const transport = createTransportPayload(formData.value);
      
      const completePayload = {
        blNumber: formData.value.blNumber,
        blType: "Original",
        status: formData.value.status,
        shipper: formData.value.shipper,
        consignee: formData.value.consignee,
        notifyParty:
          formData.value.notifyParty?.name || formData.value.notifyParty?.address
            ? `${formData.value.notifyParty.name}\n${formData.value.notifyParty.address}`
            : null,
        vessel: formData.value.vessel || null,
        voyage: formData.value.voyage || null,
        portOfLoading: formData.value.portOfLoading || null,
        portOfDischarge: formData.value.portOfDischarge || null,
        placeOfDelivery: formData.value.placeOfDelivery || null,
        transportType: formData.value.transportType,
        driver: formData.value.driver || null,
        trainNumber: formData.value.trainNumber || null,
        truckNumber: formData.value.truckNumber || null,
        commodity: toApiCommodity(formData.value.commodity),
        // Commercial / documentation fields
        bookingNumber: formData.value.bookingNumber || null,
        shippingLine: formData.value.shippingLine || null,
        incoterms: formData.value.incoterms || null,
        freightPayableAt: formData.value.freightPayableAt || null,
        bolDate: formData.value.bolDate ? dateInputToISO(formData.value.bolDate) : null,
        houseBolNumber: formData.value.houseBolNumber || null,
        masterBolNumber: formData.value.masterBolNumber || null,
        items: normalizedItems,
        transport,
      };

      const payload = createCleanPayload(completePayload);
      
      await billOfLadingService.update(blId.value, payload);

      emit("submit", {});
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || String(e);
      console.error("Failed to save changes:", msg, e);
      alert(t("billOfLadingForm.dialog.failedToSaveChanges", { msg }));
    }
  } finally {
    isSubmitting.value = false;
  }
};





/* -------------------------- Draft Invoice Generation ----------------------- */

const generateInvoice = async () => {
  if (!blId.value) return;
  isGeneratingInvoice.value = true;
  try {
    const { invoiceId } = await invoiceService.generateDraft(
      blId.value,
      formData.value.shipper
    );
    if (invoiceId) {
      invoicePreview.value = {
        id: invoiceId,
        url: invoiceService.getInvoicePreviewUrl(invoiceId),
      };
    }
  } catch (error) {
    alert(t("billOfLadingForm.dialog.failedToGenerateInvoice"));
  } finally {
    isGeneratingInvoice.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Header -->
    <div class="bg-white border-b border-gray-200 sticky top-0 z-40">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <div class="flex items-center space-x-4">
            <button 
              @click="emit('cancel')" 
              class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <ArrowLeft class="h-5 w-5" />
            </button>
            <div>
              <h1 class="text-xl font-semibold text-gray-900">
                {{ editMode ? t("billOfLadingForm.header.editTitle") : t("billOfLadingForm.header.newTitle") }}
              </h1>
              <p class="text-sm text-gray-500 mt-0.5">
                {{ editMode ? t("billOfLadingForm.header.editSubtitle", { number: formData.blNumber || t("billOfLadingForm.header.draft") }) : t("billOfLadingForm.header.newSubtitle") }}
              </p>
            </div>
          </div>
          
          <div class="flex items-center space-x-3">
            <button
              v-if="editMode"
              @click="generateInvoice"
              :disabled="isGeneratingInvoice"
              class="inline-flex items-center px-4 py-2 text-sm font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg hover:bg-emerald-100 transition-colors disabled:opacity-50"
            >
              <Loader2 v-if="isGeneratingInvoice" class="h-4 w-4 mr-2 animate-spin" />
              <FileText v-else class="h-4 w-4 mr-2" />
              {{ t("billOfLadingForm.button.generateInvoice") }}
            </button>
            
            <button
              @click="handleSubmit"
              :disabled="isSubmitting"
              class="inline-flex items-center px-6 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-lg hover:bg-blue-700 focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors disabled:opacity-50"
            >
              <Loader2 v-if="isSubmitting" class="h-4 w-4 mr-2 animate-spin" />
              <Save v-else class="h-4 w-4 mr-2" />
              {{ editMode ? t("billOfLadingForm.button.saveChanges") : t("billOfLadingForm.button.createBillOfLading") }}
            </button>
          </div>
        </div>
      </div>
    </div>

   <!-- Progress Steps -->
<div class="bg-white border-b border-gray-200">
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
    <div class="py-6">
      <nav :aria-label="t('billOfLadingForm.aria.progress')">
        <ol class="flex items-center justify-between">
          <li
            v-for="(step, stepIdx) in steps"
            :key="step.id"
            class="relative flex-1 flex flex-col items-center"
          >
            <button
              @click="goToStep(step.id)"
              class="group flex flex-col items-center w-full focus:outline-none"
            >
              <!-- Bullet + connectors wrapper (fixed height so line stays centered) -->
              <div class="relative w-full h-8 flex items-center justify-center">
                <!-- left half connector (prev -> current) -->
                <div
                  v-if="stepIdx !== 0"
                  :class="[
                    'absolute top-1/2 -translate-y-1/2 left-0 right-1/2 h-0.5',
                    currentStepIndex >= stepIdx ? 'bg-blue-600' : 'bg-gray-200'
                  ]"
                  aria-hidden="true"
                />
                <!-- right half connector (current -> next) -->
                <div
                  v-if="stepIdx !== steps.length - 1"
                  :class="[
                    'absolute top-1/2 -translate-y-1/2 left-1/2 right-0 h-0.5',
                    currentStepIndex > stepIdx ? 'bg-blue-600' : 'bg-gray-200'
                  ]"
                  aria-hidden="true"
                />

                <!-- Bullet -->
                <div
                  :class="[
                    'relative z-10 flex h-8 w-8 items-center justify-center rounded-full transition-colors',
                    (activeStep === step.id || currentStepIndex > stepIdx)
                      ? 'bg-blue-600 text-white'
                      : 'bg-white border-2 border-gray-300 text-gray-500'
                  ]"
                >
                  <component :is="step.icon" class="h-4 w-4" />
                </div>
              </div>

              <!-- Label under bullet (minimalist type) -->
              <span
                :class="[
                  'mt-2 text-[13px] leading-5 font-medium tracking-wide',
                  activeStep === step.id
                    ? 'text-blue-600'
                    : currentStepIndex > stepIdx
                    ? 'text-gray-800'
                    : 'text-gray-400'
                ]"
              >
                {{ step.label }}
              </span>
            </button>
          </li>
        </ol>
      </nav>
    </div>
  </div>
</div>


    <!-- Main Content -->
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="bg-white rounded-xl shadow-sm border border-gray-200">
        
        <!-- Basic -->
        <div v-show="activeStep === 'basic'" class="p-8">
          <div class="mb-8">
            <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ t("billOfLadingForm.section.basicInformation") }}</h2>
            <p class="text-gray-600">{{ t("billOfLadingForm.section.basicInformationSub") }}</p>
          </div>

          <!-- Identifiants principaux -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
            <Input
              v-model="formData.blNumber"
              :label="t('billOfLadingForm.field.blNumber')"
              :placeholder="t('billOfLadingForm.placeholder.blNumber')"
              required
              :error="errors.blNumber"
            />
            <Select v-model="formData.status" :label="t('billOfLadingForm.field.status')" :error="errors.status">
              <option value="Draft">{{ t("billOfLadingForm.status.draft") }}</option>
              <option value="Final">{{ t("billOfLadingForm.status.final") }}</option>
              <option value="Cancelled">{{ t("billOfLadingForm.status.cancelled") }}</option>
            </Select>
            <Input
              v-model="formData.bookingNumber"
              :label="t('billOfLadingForm.field.bookingNumber')"
              :placeholder="t('billOfLadingForm.placeholder.bookingNumber')"
              :error="errors.bookingNumber"
            />
            <Input
              v-model="formData.bolDate"
              :label="t('billOfLadingForm.field.bolDate')"
              type="date"
              :error="errors.bolDate"
            />
          </div>

          <!-- Références & conditions commerciales -->
          <div class="border-t border-gray-100 pt-6 mb-8">
            <h3 class="text-sm font-semibold text-gray-700 mb-4 flex items-center gap-2">
              <span class="w-3 h-3 rounded-full bg-amber-400 inline-block"></span>
              {{ t("billOfLadingForm.section.referencesConditions") }}
            </h3>
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div>
                <ThirdPartyAutocomplete
                  v-model="formData.shippingLine"
                  :label="t('billOfLadingForm.field.shippingLine')"
                  industry-type="Shipping Line"
                  :placeholder="t('billOfLadingForm.placeholder.shippingLine')"
                  :input-class="getInputClasses('shippingLine')"
                />
                <p v-if="errors.shippingLine" class="mt-1 text-sm text-red-600">{{ errors.shippingLine }}</p>
              </div>
              <Select v-model="formData.incoterms" :label="t('billOfLadingForm.field.incoterms')" :error="errors.incoterms">
                <option value="">{{ t("billOfLadingForm.option.notDefined") }}</option>
                <option value="EXW">EXW – Ex Works</option>
                <option value="FCA">FCA – Free Carrier</option>
                <option value="FAS">FAS – Free Alongside Ship</option>
                <option value="FOB">FOB – Free On Board</option>
                <option value="CFR">CFR – Cost &amp; Freight</option>
                <option value="CIF">CIF – Cost, Insurance &amp; Freight</option>
                <option value="CPT">CPT – Carriage Paid To</option>
                <option value="CIP">CIP – Carriage &amp; Insurance Paid</option>
                <option value="DAP">DAP – Delivered At Place</option>
                <option value="DPU">DPU – Delivered at Place Unloaded</option>
                <option value="DDP">DDP – Delivered Duty Paid</option>
              </Select>
              <Select v-model="formData.freightPayableAt" :label="t('billOfLadingForm.field.freightPayableAt')" :error="errors.freightPayableAt">
                <option value="">{{ t("billOfLadingForm.option.notDefined") }}</option>
                <option value="PREPAID">{{ t("billOfLadingForm.option.prepaid") }}</option>
                <option value="COLLECT">{{ t("billOfLadingForm.option.collect") }}</option>
                <option value="BOTH">{{ t("billOfLadingForm.option.both") }}</option>
              </Select>
              <div></div>
              <Input
                v-model="formData.houseBolNumber"
                :label="t('billOfLadingForm.field.houseBolNumber')"
                :placeholder="t('billOfLadingForm.placeholder.houseBolNumber')"
                :error="errors.houseBolNumber"
              />
              <Input
                v-model="formData.masterBolNumber"
                :label="t('billOfLadingForm.field.masterBolNumber')"
                :placeholder="t('billOfLadingForm.placeholder.masterBolNumber')"
                :error="errors.masterBolNumber"
              />
            </div>
          </div>

          <!-- Quick Stats -->
          <div class="bg-gray-50 rounded-lg p-4 mb-6">
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
              <div>
                <div class="text-2xl font-bold text-blue-600">{{ formData.items.length }}</div>
                <div class="text-xs text-gray-500">{{ t("billOfLadingForm.stats.units") }}</div>
              </div>
              <div>
                <div class="text-2xl font-bold text-green-600">{{ totalWeightKg.toFixed(1) }}</div>
                <div class="text-xs text-gray-500">{{ t("billOfLadingForm.stats.weightKg") }}</div>
              </div>
              <div>
                <div class="text-lg font-semibold text-amber-600">{{ formData.incoterms || '—' }}</div>
                <div class="text-xs text-gray-500">{{ t("billOfLadingForm.field.incoterms") }}</div>
              </div>
              <div>
                <div class="text-lg font-semibold text-slate-600">{{ formData.freightPayableAt || '—' }}</div>
                <div class="text-xs text-gray-500">{{ t("billOfLadingForm.stats.freight") }}</div>
              </div>
            </div>
          </div>

          <div class="flex justify-end">
            <button
              @click="nextStep"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {{ t("billOfLadingForm.button.nextParties") }}
            </button>
          </div>
        </div>

        <!-- Parties -->
        <div v-show="activeStep === 'parties'" class="p-8">
          <div class="mb-8">
            <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ t("billOfLadingForm.section.partyInformation") }}</h2>
            <p class="text-gray-600">{{ t("billOfLadingForm.section.partyInformationSub") }}</p>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <div class="space-y-6">
              <div>
                <ThirdPartyAutocomplete
                  v-model="formData.shipper"
                  :label="t('billOfLadingForm.field.shipper')"
                  required
                  :placeholder="t('billOfLadingForm.placeholder.shipperName')"
                  :input-class="getInputClasses('shipper')"
                />
                <p v-if="errors.shipper" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.shipper }}
                </p>
              </div>

              <div>
                <ThirdPartyAutocomplete
                  v-model="formData.consignee"
                  :label="t('billOfLadingForm.field.consignee')"
                  required
                  :placeholder="t('billOfLadingForm.placeholder.consigneeName')"
                  :input-class="getInputClasses('consignee')"
                />
                <p v-if="errors.consignee" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.consignee }}
                </p>
              </div>
            </div>

            <div>
              <div>
                <ThirdPartyAutocomplete
                  v-model="formData.notifyParty.name"
                  :label="t('billOfLadingForm.field.notifyParty')"
                  :placeholder="t('billOfLadingForm.placeholder.notifyPartyDetails')"
                  :input-class="getInputClasses('notifyParty')"
                  @select="onNotifyPartySelect"
                />
                <textarea
                  v-model="formData.notifyParty.address"
                  rows="3"
                  data-testid="notify-party-address"
                  :placeholder="t('billOfLadingForm.placeholder.notifyPartyDetails')"
                  :class="getInputClasses('notifyPartyAddress')"
                  class="mt-2"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="flex justify-between mt-8">
            <button
              @click="prevStep"
              class="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              {{ t("invoices.button.previous") }}
            </button>
            <button
              @click="nextStep"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {{ t("billOfLadingForm.button.nextTransport") }}
            </button>
          </div>
        </div>

        <!-- Transport -->
        <div v-show="activeStep === 'transport'" class="p-8">
          <div class="mb-8">
            <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ t("billOfLadingForm.section.transportInformation") }}</h2>
            <p class="text-gray-600">{{ t("billOfLadingForm.section.transportInformationSub") }}</p>
          </div>

          <div class="space-y-8">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-4">{{ t("billOfLadingForm.field.transportType") }}</label>
              <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div
                  v-for="type in ['Vessel', 'Train', 'Truck']"
                  :key="type"
                  @click="formData.transportType = type"
                  :class="[
                    'relative flex cursor-pointer rounded-lg border p-4 focus:outline-none',
                    formData.transportType === type
                      ? 'border-blue-600 bg-blue-50'
                      : 'border-gray-300 bg-white hover:bg-gray-50',
                    formData.status === 'Final' ? 'cursor-not-allowed opacity-50' : ''
                  ]"
                >
                  <div class="flex items-center">
                    <div class="flex-shrink-0">
                      <component :is="TRANSPORT_ICONS[type]" class="h-6 w-6 text-gray-600" />
                    </div>
                    <div class="ml-3">
                      <div class="text-sm font-medium text-gray-900">{{ type }}</div>
                    </div>
                  </div>
                  <div
                    v-if="formData.transportType === type"
                    class="absolute -inset-px rounded-lg border-2 border-blue-600 pointer-events-none"
                  />
                </div>
              </div>
            </div>

            <!-- Vessel -->
            <div v-if="formData.transportType === 'Vessel'" class="space-y-6">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  <Search class="inline h-4 w-4 mr-1" />
                  {{ t("billOfLadingForm.field.searchVesselVisit") }}
                </label>
                <input
                  v-model="vesselQuery"
                  type="text"
                  :disabled="formData.status === 'Final'"
                  :placeholder="t('billOfLadingForm.placeholder.searchVesselVisit')"
                  :class="getInputClasses('vessel')"
                />
                <div v-if="vesselSearchResults.length" class="relative">
                  <div class="absolute z-10 mt-1 w-full bg-white shadow-lg max-h-60 rounded-lg border border-gray-200 overflow-auto">
                    <div
                      v-for="visit in vesselSearchResults"
                      :key="visit.id"
                      @click="selectVesselVisit(visit)"
                      class="cursor-pointer px-4 py-3 hover:bg-blue-50 border-b border-gray-100 last:border-b-0"
                    >
                      <div class="font-medium text-gray-900">{{ visit.vesselName }}</div>
                      <div class="text-sm text-gray-500">
                        {{ t("billOfLadingForm.vesselResult.info", { imo: visit.imo || '-', voyageIn: visit.voyageIn || '-', voyageOut: visit.voyageOut || '-', terminal: visit.terminal || '-', eta: visit.eta || '-', etd: visit.etd || '-' }) }}
                      </div>
                    </div>
                  </div>
                </div>
                <p v-if="errors.vessel" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors.vessel }}
                </p>
              </div>

              <!-- Vessel Snapshot -->
              <div v-if="formData.transportSnapshot" class="bg-gray-50 rounded-lg p-6">
                <div class="flex justify-between items-center mb-4">
                  <h3 class="text-sm font-medium text-gray-900">{{ t("billOfLadingForm.label.selectedVesselVisit") }}</h3>
                  <div class="flex space-x-2">
                    <button
                      v-if="!refreshDisabled && formData.status !== 'Final'"
                      type="button"
                      @click="refreshSnapshot"
                      :disabled="isRefreshingTransport"
                      class="text-sm text-blue-600 hover:text-blue-900"
                    >
                      {{ isRefreshingTransport ? t("billOfLadingForm.button.refreshing") : t("billOfLadingForm.button.refresh") }}
                    </button>
                    <a
                      v-if="formData.transportSnapshot.id"
                      :href="`/vessel-visits/${formData.transportSnapshot.id}`"
                      class="text-sm text-blue-600 hover:text-blue-900"
                    >
                      {{ t("billOfLadingForm.button.viewDetails") }}
                    </a>
                  </div>
                </div>

                <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div v-for="field in [
                    { key: 'vesselName', label: t('billOfLadingForm.vessel.vesselName') },
                    { key: 'imo', label: t('billOfLadingForm.vessel.imo') },
                    { key: 'callSign', label: t('billOfLadingForm.vessel.callSign') },
                    { key: 'operator', label: t('billOfLadingForm.vessel.operator') },
                    { key: 'port', label: t('billOfLadingForm.vessel.port') },
                    { key: 'terminal', label: t('billOfLadingForm.vessel.terminal') },
                    { key: 'berth', label: t('billOfLadingForm.vessel.berth') },
                    { key: 'eta', label: t('billOfLadingForm.vessel.eta') }
                  ]" :key="field.key" class="space-y-1">
                    <label class="text-xs font-medium text-gray-500 uppercase tracking-wide">
                      {{ field.label }}
                    </label>
                    <div class="text-sm text-gray-900">
                      {{ formData.transportSnapshot[field.key] || '-' }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Train -->
            <div v-else-if="formData.transportType === 'Train'" class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Input
                v-model="formData.driver"
                :label="t('billOfLadingForm.field.driver')"
                :placeholder="t('billOfLadingForm.placeholder.driverName')"
                required
                :error="errors.driver"
              />
              <Input
                v-model="formData.trainNumber"
                :label="t('billOfLadingForm.field.trainNumber')"
                :placeholder="t('billOfLadingForm.placeholder.trainNumber')"
                required
                :error="errors.trainNumber"
              />
            </div>

            <!-- Truck -->
            <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Input
                v-model="formData.driver"
                :label="t('billOfLadingForm.field.driver')"
                :placeholder="t('billOfLadingForm.placeholder.driverName')"
                required
                :error="errors.driver"
              />
              <Input
                v-model="formData.truckNumber"
                :label="t('billOfLadingForm.field.truckNumber')"
                :placeholder="t('billOfLadingForm.placeholder.truckNumber')"
                required
                :error="errors.truckNumber"
              />
            </div>
          </div>

          <div class="flex justify-between mt-8">
            <button
              @click="prevStep"
              class="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              {{ t("invoices.button.previous") }}
            </button>
            <button
              @click="nextStep"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {{ t("billOfLadingForm.button.nextCommodity") }}
            </button>
          </div>
        </div>

        <!-- Commodity -->
        <div v-show="activeStep === 'commodity'" class="p-8">
          <div class="mb-8">
            <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ t("billOfLadingForm.section.commodityInformation") }}</h2>
            <p class="text-gray-600">{{ t("billOfLadingForm.section.commodityInformationSub") }}</p>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div class="lg:col-span-2 space-y-6">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">
                  {{ t("billOfLadingForm.field.description") }} <span class="text-red-500">*</span>
                </label>
                <textarea
                  v-model="formData.commodity.description"
                  rows="4"
                  :placeholder="t('billOfLadingForm.placeholder.commodityDescription')"
                  :class="getInputClasses('commodity.description')"
                ></textarea>
                <p v-if="errors['commodity.description']" class="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-1" />
                  {{ errors["commodity.description"] }}
                </p>
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">
                    {{ t("billOfLadingForm.field.numberOfPackages") }} <span class="text-red-500">*</span>
                  </label>
                  <input
                    v-model.number="formData.commodity.packagesNumber"
                    type="number"
                    min="1"
                    placeholder="0"
                    :class="getInputClasses('commodity.packagesNumber')"
                  />
                  <p v-if="errors['commodity.packagesNumber']" class="mt-2 text-sm text-red-600 flex items-center">
                    <AlertCircle class="h-4 w-4 mr-1" />
                    {{ errors["commodity.packagesNumber"] }}
                  </p>
                </div>

                <div class="flex items-center pt-8">
                  <input
                    v-model="formData.commodity.hazardous"
                    type="checkbox"
                    class="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <label class="ml-2 text-sm text-gray-700 font-medium">
                    {{ t("billOfLadingForm.field.hazardousCargo") }}
                  </label>
                </div>
              </div>

              <div v-if="formData.commodity.hazardous" class="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <h3 class="text-sm font-medium text-amber-800 mb-3 flex items-center">
                  <AlertCircle class="h-4 w-4 mr-2" />
                  {{ t("billOfLadingForm.label.hazardousCargoInformation") }}
                </h3>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label class="block text-xs font-medium text-amber-700 mb-1">{{ t("billOfLadingForm.field.hazardClass") }}</label>
                    <input
                      v-model="formData.commodity.hazardClass"
                      type="text"
                      :placeholder="t('billOfLadingForm.placeholder.hazardClass')"
                      class="block w-full px-3 py-2 text-sm border border-amber-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent bg-white"
                    />
                  </div>
                  <div>
                    <label class="block text-xs font-medium text-amber-700 mb-1">{{ t("billOfLadingForm.field.unNumber") }}</label>
                    <input
                      v-model="formData.commodity.unNumber"
                      type="text"
                      :placeholder="t('billOfLadingForm.placeholder.unNumber')"
                      class="block w-full px-3 py-2 text-sm border border-amber-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-transparent bg-white"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div class="bg-gray-50 rounded-lg p-6">
              <h3 class="text-sm font-medium text-gray-900 mb-4">{{ t("billOfLadingForm.label.commoditySummary") }}</h3>
              <div class="space-y-4">
                <div class="flex justify-between">
                  <span class="text-sm text-gray-500">{{ t("billOfLadingForm.label.totalWeight") }}</span>
                  <span class="text-sm font-medium text-gray-900">{{ totalWeightKg.toFixed(1) }} kg</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-sm text-gray-500">{{ t("billOfLadingForm.label.totalVolume") }}</span>
                  <span class="text-sm font-medium text-gray-900">{{ totalVolumeM3.toFixed(1) }} m³</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-sm text-gray-500">{{ t("billOfLading.field.packages") }}</span>
                  <span class="text-sm font-medium text-gray-900">{{ formData.commodity.packagesNumber || 0 }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-sm text-gray-500">{{ t("billOfLadingForm.label.hazardous") }}</span>
                  <span :class="[
                    'text-sm font-medium',
                    formData.commodity.hazardous ? 'text-amber-600' : 'text-gray-900'
                  ]">
                    {{ formData.commodity.hazardous ? t("common.yes") : t("common.no") }}
                  </span>
                </div>
              </div>
              <div class="mt-4 pt-4 border-t border-gray-200 text-xs text-gray-500">
                {{ t("billOfLadingForm.label.weightVolumeCalculated") }}
              </div>
            </div>
          </div>

          <div class="flex justify-between mt-8">
            <button
              @click="prevStep"
              class="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              {{ t("invoices.button.previous") }}
            </button>
            <button
              @click="nextStep"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {{ t("billOfLadingForm.button.nextItems") }}
            </button>
          </div>
        </div>

        <!-- Items -->
        <div v-show="activeStep === 'items'" class="p-8">
          <div class="mb-8">
            <div class="flex justify-between items-center">
              <div>
                <h2 class="text-lg font-semibold text-gray-900 mb-2">{{ t("billOfLadingForm.section.items") }}</h2>
                <p class="text-gray-600">{{ t("billOfLadingForm.section.itemsSub") }}</p>
              </div>
              <button
                @click="addItem"
                class="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus class="h-4 w-4 mr-2" />
                {{ t("billOfLadingForm.button.addItem") }}
              </button>
            </div>
          </div>

          <p v-if="errors.items" class="mb-6 p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg flex items-center">
            <AlertCircle class="h-4 w-4 mr-2" />
            {{ errors.items }}
          </p>

          <div v-if="formData.items.length === 0" class="text-center py-12 bg-gray-50 rounded-lg border-2 border-dashed border-gray-300">
            <Package class="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <h3 class="text-sm font-medium text-gray-900 mb-2">{{ t("billOfLadingForm.label.noItemsYet") }}</h3>
            <p class="text-sm text-gray-500 mb-4">{{ t("billOfLadingForm.label.noItemsHint") }}</p>
            <button
              @click="addItem"
              class="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t("billOfLadingForm.button.addFirstItem") }}
            </button>
          </div>

          <div v-else class="space-y-4">
            <div
              v-for="(item, index) in formData.items"
              :key="item.clientId"
              class="bg-white border border-gray-200 rounded-lg overflow-hidden"
            >
              <div class="p-6">
                <div class="flex items-center justify-between">
                  <div class="flex items-center space-x-4">
                    <button
                      @click="toggleItem(item)"
                      class="p-1 text-gray-400 hover:text-gray-600 transition-colors"
                    >
                      <ChevronDown v-if="!item.expanded" class="h-5 w-5" />
                      <ChevronUp v-else class="h-5 w-5" />
                    </button>
                    
                    <div class="flex items-center space-x-3">
                      <div :class="[
                        'w-10 h-10 rounded-lg flex items-center justify-center',
                        item.itemType === 'container' ? 'bg-blue-100 text-blue-600' :
                        item.itemType === 'vehicle' ? 'bg-green-100 text-green-600' :
                        'bg-purple-100 text-purple-600'
                      ]">
                        <Package class="h-5 w-5" />
                      </div>
                      
                      <div>
                        <div class="flex items-center space-x-2">
                          <input
                            v-model="item.itemNumber"
                            :placeholder="t('billOfLadingForm.placeholder.itemNumber')"
                            class="text-sm font-medium bg-transparent border-none p-0 focus:ring-0 focus:outline-none"
                            :class="item.errors?.itemNumber ? 'text-red-600' : 'text-gray-900'"
                          />
                          <span class="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded-full capitalize">
                            {{ item.itemType }}
                          </span>
                        </div>
                        <p v-if="item.errors?.itemNumber" class="text-xs text-red-600 mt-1">
                          {{ item.errors.itemNumber }}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div class="flex items-center space-x-2">
                    <button
                      @click="duplicateItem(index)"
                      class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                      :title="t('billOfLadingForm.button.duplicateItem')"
                    >
                      <Copy class="h-4 w-4" />
                    </button>
                    <button
                      @click="removeItem(index)"
                      class="p-2 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :title="t('billOfLadingForm.button.removeItem')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </div>

                <div class="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <label class="block text-xs font-medium text-gray-500 mb-1">{{ t("billOfLading.itemsModal.colType") }}</label>
                    <select
                      v-model="item.itemType"
                      :class="getItemInputClasses(item, 'itemType')"
                    >
                      <option value="container">{{ t("billOfLadingForm.itemType.container") }}</option>
                      <option value="breakbulk">{{ t("billOfLadingForm.itemType.breakbulk") }}</option>
                      <option value="vehicle">{{ t("billOfLadingForm.itemType.vehicle") }}</option>
                    </select>
                  </div>

                  <div>
                    <label class="block text-xs font-medium text-gray-500 mb-1">{{ t("billOfLadingForm.field.weightKg") }}</label>
                    <input
                      v-model.number="item.weightKg"
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="0.00"
                      :class="getItemInputClasses(item, 'weightKg')"
                    />
                  </div>

                  <div>
                    <label class="block text-xs font-medium text-gray-500 mb-1">{{ t("billOfLadingForm.field.volumeM3") }}</label>
                    <input
                      v-model.number="item.volumeM3"
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="0.00"
                      :class="getItemInputClasses(item, 'volumeM3')"
                    />
                  </div>

                  <div>
                    <label class="block text-xs font-medium text-gray-500 mb-1">{{ t("invoices.column.status") }}</label>
                    <select
                      v-model="item.status"
                      :class="getItemInputClasses(item, 'status')"
                    >
                      <option value="Pending">{{ t("billOfLadingForm.itemStatus.pending") }}</option>
                      <option value="Loaded">{{ t("billOfLadingForm.itemStatus.loaded") }}</option>
                      <option value="Discharged">{{ t("billOfLadingForm.itemStatus.discharged") }}</option>
                      <option value="In Yard">{{ t("billOfLadingForm.itemStatus.inYard") }}</option>
                      <option value="Preadvised">{{ t("billOfLadingForm.itemStatus.preadvised") }}</option>
                    </select>
                  </div>
                </div>
              </div>

              <div v-if="item.expanded" class="bg-gray-50 px-6 py-4 border-t border-gray-200">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <Input
                    v-model="item.ownerId"
                    :label="t('billOfLadingForm.field.ownerId')"
                    :placeholder="t('billOfLadingForm.placeholder.ownerId')"
                    required
                    :error="item.errors?.ownerId"
                  />

                  <Input
                    v-model="item.position"
                    :label="t('billOfLadingForm.field.position')"
                    :placeholder="t('billOfLadingForm.placeholder.position')"
                  />

                  <Select
                    v-if="item.itemType === 'container'"
                    v-model="item.type"
                    :label="t('billOfLadingForm.field.containerType')"
                    :error="item.errors?.type"
                  >
                    <option value="">{{ t("billOfLadingForm.option.selectContainerType") }}</option>
                    <option v-for="c in activeIsoCodes" :key="c.code" :value="c.code">
                      {{ c.code }} — {{ c.description }}
                    </option>
                  </Select>
                  <Input
                    v-else
                    v-model="item.type"
                    :label="t('billOfLadingForm.field.subtype')"
                    :placeholder="item.itemType === 'vehicle' ? t('billOfLadingForm.placeholder.vehicleType') : t('billOfLadingForm.placeholder.enterType')"
                    :error="item.errors?.type"
                  />

                  <div></div>

                  <Input
                    v-model="item.lastInspection"
                    :label="t('billOfLadingForm.field.lastInspectionDate')"
                    type="date"
                    @input="handleLastInspectionChange(item)"
                  />

                  <Input
                    v-model="item.nextInspection"
                    :label="t('billOfLadingForm.field.nextInspectionDate')"
                    type="date"
                    :error="item.errors?.nextInspection"
                  />

                  <div class="md:col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-2">{{ t("payments.field.notes") }}</label>
                    <textarea
                      v-model="item.notes"
                      rows="3"
                      :placeholder="t('billOfLadingForm.placeholder.additionalNotes')"
                      :class="getItemInputClasses(item, 'notes')"
                    ></textarea>
                  </div>
                </div>

                <div class="flex justify-end mt-4">
                  <button
                    @click="saveItem(item)"
                    class="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                  >
                    {{ t("billOfLadingForm.button.saveItem") }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="flex justify-between mt-8">
            <button
              @click="prevStep"
              class="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              {{ t("invoices.button.previous") }}
            </button>
            <button
              @click="handleSubmit"
              :disabled="isSubmitting"
              class="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
            >
              <Loader2 v-if="isSubmitting" class="h-4 w-4 mr-2 animate-spin" />
              {{ editMode ? t("billOfLadingForm.button.saveChanges") : t("billOfLadingForm.button.createBillOfLading") }}
            </button>
          </div>
        </div>

      </div>
    </div>

    <!-- Invoice Preview Modal -->
    <InvoicePreview
      v-if="invoicePreview"
      :invoice-id="invoicePreview.id"
      :preview-url="invoicePreview.url"
      status="Draft"
      @close="invoicePreview = null"
    />
  </div>
</template>

<style scoped>
.max-h-60::-webkit-scrollbar { width: 8px; }
.max-h-60::-webkit-scrollbar-track { background: #f1f5f9; }
.max-h-60::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.max-h-60::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
.transition-all { transition-property: all; transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1); transition-duration: 200ms; }
input:focus, select:focus, textarea:focus { outline: 2px solid transparent; outline-offset: 2px; }
.hover\:shadow-md:hover { box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1); transition: box-shadow 0.2s ease-in-out; }
button { transition: all 0.2s ease-in-out; }
button:hover { transform: translateY(-1px); }
button:active { transform: translateY(0); }
.focus\:ring-2:focus { box-shadow: 0 0 0 2px rgb(59 130 246 / 0.5); }
.border-red-300 { animation: shake 0.5s ease-in-out; }
@keyframes shake { 0%, 100% { transform: translateX(0);} 25% { transform: translateX(-2px);} 75% { transform: translateX(2px);} }
.animate-spin { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg);} to { transform: rotate(360deg);} }
.bg-white:hover { box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1); transition: box-shadow 0.2s ease-in-out; }
input[type="radio"]:checked, input[type="checkbox"]:checked { background-color: #3b82f6; border-color: #3b82f6; }
.progress-bar { transition: width 0.3s ease-in-out; }
@media (max-width: 640px) {
  .grid-cols-2 { grid-template-columns: repeat(1, minmax(0, 1fr)); }
  .md\:grid-cols-4 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>