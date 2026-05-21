import { watch, isRef, toRaw, type WatchSource } from 'vue';

const STORAGE_PREFIX = 'tas:state:';
const SCHEMA_VERSION = 1;

interface PersistedPayload {
  v: number;
  data: Record<string, unknown>;
}

interface PersistOptions<T extends Record<string, unknown>> {
  /** Storage key suffix — the final key is `tas:state:${key}`. */
  key: string;
  /** Reactive state container (reactive() or a plain object of refs). */
  state: T;
  /** Subset of keys on `state` to persist. Omit to persist all enumerable keys. */
  keys?: (keyof T)[];
  /** Debounce in ms before writing. Default 250ms. */
  debounce?: number;
}

function storageKey(key: string) {
  return `${STORAGE_PREFIX}${key}`;
}

function safeParse(raw: string | null): PersistedPayload | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && parsed.v === SCHEMA_VERSION) {
      return parsed as PersistedPayload;
    }
  } catch {
    // fall through
  }
  return null;
}

function readValue<T>(container: T, prop: keyof T): unknown {
  const v = container[prop];
  return isRef(v) ? v.value : v;
}

function writeValue<T>(container: T, prop: keyof T, value: unknown): void {
  const current = container[prop];
  if (isRef(current)) {
    current.value = value;
    return;
  }
  if (current !== null && typeof current === 'object' && value !== null && typeof value === 'object') {
    // Merge into existing reactive object to preserve reactivity.
    Object.assign(current as object, value);
    return;
  }
  (container as Record<string, unknown>)[prop as string] = value;
}

/**
 * Hydrate a reactive state container from localStorage and persist future
 * changes. Safe to call during a store's setup — it reads once synchronously
 * and then attaches a debounced watcher.
 *
 * Failures (quota, invalid JSON, schema mismatch) are swallowed and logged.
 */
export function persistState<T extends Record<string, unknown>>(opts: PersistOptions<T>): void {
  const { key, state, keys: rawKeys, debounce = 250 } = opts;
  const keys = (rawKeys ?? (Object.keys(state) as (keyof T)[]));
  const fullKey = storageKey(key);

  // 1) Hydrate (merge) on init.
  try {
    const payload = safeParse(localStorage.getItem(fullKey));
    if (payload) {
      for (const k of keys) {
        if (k in payload.data) {
          writeValue(state, k, payload.data[k as string]);
        }
      }
    }
  } catch (e) {
    console.warn(`[persistState] failed to hydrate "${key}":`, e);
  }

  // 2) Watch + persist.
  const sources: WatchSource[] = keys.map((k) => () => readValue(state, k));
  let timer: ReturnType<typeof setTimeout> | null = null;

  watch(
    sources,
    () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        try {
          const data: Record<string, unknown> = {};
          for (const k of keys) {
            const v = readValue(state, k);
            // toRaw to drop reactive proxies; JSON.stringify handles the rest.
            data[k as string] = v && typeof v === 'object' ? toRaw(v) : v;
          }
          const payload: PersistedPayload = { v: SCHEMA_VERSION, data };
          localStorage.setItem(fullKey, JSON.stringify(payload));
        } catch (e) {
          // Quota / private-mode etc. — non-fatal.
          console.warn(`[persistState] failed to persist "${key}":`, e);
        }
      }, debounce);
    },
    { deep: true },
  );
}

/** Clear a persisted state entry (useful for "reset all filters" flows). */
export function clearPersistedState(key: string): void {
  try {
    localStorage.removeItem(storageKey(key));
  } catch {
    // ignore
  }
}
