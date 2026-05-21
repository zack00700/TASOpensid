import { ref, computed } from 'vue';
import { persistState } from '../utils/persistState';

export interface ColumnDefinition {
  /** Stable key — used in storage and for toggles. Never change existing keys. */
  key: string;
  /** Human-readable label shown in the picker UI. */
  label: string;
  /** If true, the column is hidden by default (still toggleable). */
  hiddenByDefault?: boolean;
  /** If true, the column cannot be hidden (always visible, not shown in picker). */
  required?: boolean;
}

/**
 * Per-view column visibility, persisted in localStorage.
 *
 * The caller owns the full column list (ColumnDefinition[]) and this composable
 * returns a reactive Set of hidden keys plus helpers.  Rendering logic stays in
 * the consumer: `v-if="isVisible(col.key)"` on each <th>/<td>.
 */
export function useColumnPreferences(storageKey: string, columns: ColumnDefinition[]) {
  const defaultHidden = columns
    .filter((c) => c.hiddenByDefault && !c.required)
    .map((c) => c.key);

  // Use a plain array in state (Set is not JSON-friendly).
  const state = { hiddenKeys: ref<string[]>([...defaultHidden]) };

  persistState({
    key: `columns:${storageKey}`,
    state: state as unknown as Record<string, unknown>,
    keys: ['hiddenKeys'],
  });

  const hiddenSet = computed(() => new Set(state.hiddenKeys.value));

  function isVisible(key: string): boolean {
    const def = columns.find((c) => c.key === key);
    if (def?.required) return true;
    return !hiddenSet.value.has(key);
  }

  function toggle(key: string): void {
    const def = columns.find((c) => c.key === key);
    if (def?.required) return;
    const current = state.hiddenKeys.value;
    if (current.includes(key)) {
      state.hiddenKeys.value = current.filter((k) => k !== key);
    } else {
      state.hiddenKeys.value = [...current, key];
    }
  }

  function reset(): void {
    state.hiddenKeys.value = [...defaultHidden];
  }

  const toggleable = computed(() => columns.filter((c) => !c.required));
  const hiddenCount = computed(() => state.hiddenKeys.value.length);

  return {
    columns,
    toggleable,
    isVisible,
    toggle,
    reset,
    hiddenCount,
  };
}
