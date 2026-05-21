<template>
  <div data-test="datatable-container" class="overflow-x-auto rounded-xl border border-slate-200 bg-white">
    <!-- Desktop table -->
    <table :class="['min-w-full', desktopVisibility]">
      <thead :class="['bg-slate-50', stickyHeader && 'sticky top-0 z-10']">
        <tr>
          <th
            v-if="selectable"
            class="sticky left-0 z-15 bg-slate-50 w-10 px-2 py-3"
            data-test="datatable-select-header"
          >
            <input
              type="checkbox"
              class="w-4 h-4 text-blue-600 border-slate-300 rounded focus:ring-blue-500"
              :checked="allSelectableSelected"
              :disabled="selectableKeys.length === 0"
              @click="toggleAll"
              :ref="(el) => { if (el) (el as HTMLInputElement).indeterminate = someSelectableSelected; }"
            />
          </th>
          <th
            v-for="col in columns"
            :key="col.key"
            :class="[headerCellBaseClass, alignClass(col.align), col.width, ...stickyClasses(col, true), col.sticky === 'left' && stickyCol?.key === col.key ? 'bg-slate-50' : '']"
          >
            <button
              v-if="col.sortable"
              type="button"
              class="flex items-center gap-1 hover:bg-slate-100 transition-colors cursor-pointer -mx-2 px-2 py-1 rounded"
              :class="alignFlexClass(col.align)"
              @click="onHeaderClick(col)"
            >
              {{ col.label }}
              <ArrowUp v-if="sortBy === col.key && sortDir === 'asc'" class="w-3 h-3 text-slate-700" />
              <ArrowDown v-else-if="sortBy === col.key && sortDir === 'desc'" class="w-3 h-3 text-slate-700" />
            </button>
            <div v-else :class="['flex items-center', alignFlexClass(col.align)]">{{ col.label }}</div>
          </th>
          <!-- Trailing actions column (always present, label hidden by default) -->
          <th class="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wider w-24">
            {{ $slots['row-actions'] ? 'Actions' : '' }}
          </th>
        </tr>
      </thead>

      <!-- Loading -->
      <tbody v-if="loading" data-test="datatable-skeleton">
        <SkeletonTable :cols="columns.length + 1 + (selectable ? 1 : 0)" />
      </tbody>

      <!-- Empty -->
      <tbody v-else-if="rows.length === 0">
        <tr>
          <td :colspan="columns.length + 1 + (selectable ? 1 : 0)">
            <slot name="empty">
              <EmptyState
                v-if="empty"
                :title="empty.title"
                :description="empty.description"
                :action-label="empty.actionLabel"
                @action="$emit('empty-action')"
              />
            </slot>
          </td>
        </tr>
      </tbody>

      <!-- Rows -->
      <tbody v-else class="divide-y divide-slate-100">
        <tr
          v-for="(row, idx) in rows"
          :key="getRowKey(row, idx)"
          data-test="datatable-row"
          :class="['group hover:bg-blue-50 transition-colors', zebra && idx % 2 === 1 && 'bg-slate-50/40']"
          @click="$emit('row-click', row)"
          @dblclick="$emit('row-dblclick', row)"
        >
          <td
            v-if="selectable"
            :class="[
              'sticky left-0 z-5 w-10 px-2 py-3',
              zebra && idx % 2 === 1 ? 'bg-slate-50' : 'bg-white',
              'group-hover:bg-blue-50',
            ]"
          >
            <input
              v-if="!props.isSelectable || props.isSelectable(row)"
              type="checkbox"
              class="w-4 h-4 text-blue-600 border-slate-300 rounded focus:ring-blue-500"
              :checked="selectedSet.has(getRowKey(row, idx))"
              @click="toggleRow(row, idx, $event)"
            />
          </td>
          <td
            v-for="col in columns"
            :key="col.key"
            :class="[
              bodyCellBaseClass,
              alignClass(col.align),
              ...stickyClasses(col, false),
              col.sticky === 'left' && stickyCol?.key === col.key
                ? [
                    zebra && idx % 2 === 1 ? 'bg-slate-50' : 'bg-white',
                    'group-hover:bg-blue-50',
                  ]
                : '',
            ]"
          >
            <slot
              :name="`cell-${col.key}`"
              :row="row"
              :value="(row as Record<string, unknown>)[col.key]"
              :index="idx"
            >{{ renderCell(col, row) }}</slot>
          </td>
          <td class="px-4 py-3 whitespace-nowrap text-right text-sm font-medium">
            <slot name="row-actions" :row="row" :index="idx" />
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Mobile fallback -->
    <div v-if="showMobileLayer && !loading && rows.length > 0" :class="['sm:hidden', mobileSlotProvided ? '' : 'p-3 space-y-3']">
      <template v-for="(row, idx) in rows" :key="getRowKey(row, idx)">
        <slot name="mobile-card" :row="row" :index="idx" :columns="columns">
          <!-- Auto card: only when at least one column has mobile=title|subtitle|meta -->
          <div
            v-if="hasMobileHints"
            data-datatable-mobile-card
            class="rounded-xl border border-slate-200 bg-white p-4"
          >
            <div v-if="mobileTitleCol" data-mobile-title class="font-semibold text-slate-900 text-sm">
              {{ renderCell(mobileTitleCol, row) }}
            </div>
            <div v-if="mobileSubtitleCol" class="text-xs text-slate-500 mt-0.5">
              {{ renderCell(mobileSubtitleCol, row) }}
            </div>
            <dl v-if="mobileMetaCols.length" class="mt-2 space-y-1">
              <div v-for="meta in mobileMetaCols" :key="meta.key" class="flex justify-between gap-2 text-xs">
                <dt class="text-slate-500">{{ meta.label }}</dt>
                <dd class="text-slate-900 font-medium">{{ renderCell(meta, row) }}</dd>
              </div>
            </dl>
          </div>
        </slot>
      </template>
    </div>

    <!-- Footer (free area for pagination) -->
    <div v-if="$slots.footer" class="border-t border-slate-200">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts" generic="Row extends Record<string, unknown>">
import { computed, useSlots, watch } from 'vue';
import { ArrowUp, ArrowDown } from 'lucide-vue-next';
import SkeletonTable from './SkeletonTable.vue';
import EmptyState from './EmptyState.vue';
import type { Column } from './DataTable.types';

const props = withDefaults(
  defineProps<{
    rows: Row[];
    columns: Column<Row>[];
    rowKey?: string | ((row: Row) => string);
    sortBy?: string;
    sortDir?: 'asc' | 'desc';
    loading?: boolean;
    empty?: { title: string; description?: string; actionLabel?: string };
    zebra?: boolean;
    stickyHeader?: boolean;
    selectable?: boolean;
    isSelectable?: (row: Row) => boolean;
    selected?: string[];
    selectAllScope?: 'page' | 'all';
  }>(),
  {
    rowKey: 'id',
    zebra: true,
    stickyHeader: true,
    selectable: false,
    selected: () => [],
    selectAllScope: 'page',
  },
);

const emit = defineEmits<{
  'update:sort': [{ by: string; dir: 'asc' | 'desc' }];
  'update:selected': [keys: string[]];
  'row-click': [row: Row];
  'row-dblclick': [row: Row];
  'empty-action': [];
}>();

const slots = useSlots();

const headerCellBaseClass = 'px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider';
const bodyCellBaseClass = 'px-4 py-3 whitespace-nowrap text-sm text-slate-900';

// Show desktop table on >=sm; on small screens, hide it ONLY if the mobile layer
// has actual content (slot provided or at least one mobile hint). Otherwise we
// fall back to overflow-x-auto on the desktop table.
const mobileSlotProvided = computed(() => !!slots['mobile-card']);
const hasMobileHints = computed(() =>
  props.columns.some((c) => c.mobile === 'title' || c.mobile === 'subtitle' || c.mobile === 'meta'),
);
const showMobileLayer = computed(() => mobileSlotProvided.value || hasMobileHints.value);
const desktopVisibility = computed(() => (showMobileLayer.value ? 'hidden sm:table' : 'table'));

const mobileTitleCol = computed(() => props.columns.find((c) => c.mobile === 'title'));
const mobileSubtitleCol = computed(() => props.columns.find((c) => c.mobile === 'subtitle'));
const mobileMetaCols = computed(() => props.columns.filter((c) => c.mobile === 'meta'));

const stickyCols = computed(() => props.columns.filter((c) => c.sticky === 'left'));
const stickyCol = computed(() => stickyCols.value[0]);
if (typeof window !== 'undefined' && import.meta.env.DEV) {
  watch(
    stickyCols,
    (cols) => {
      if (cols.length > 1) {
        console.warn(
          '[DataTable] Multiple columns flagged sticky="left"; only the first one will be sticky in v2.',
          cols.map((c) => c.key),
        );
      }
    },
    { immediate: true },
  );
}

function alignClass(align: Column<Row>['align']): string {
  return align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left';
}
function alignFlexClass(align: Column<Row>['align']): string {
  return align === 'right' ? 'justify-end' : align === 'center' ? 'justify-center' : 'justify-start';
}
function stickyClasses(col: Column<Row>, isHeader: boolean): string[] {
  if (col.sticky !== 'left' || stickyCol.value?.key !== col.key) return [];
  const left = props.selectable ? 'left-[40px]' : 'left-0';
  const z = isHeader ? 'z-15' : 'z-5';
  return ['sticky', left, z];
}

function getRowKey(row: Row, idx: number): string {
  if (typeof props.rowKey === 'function') return props.rowKey(row);
  const v = (row as Record<string, unknown>)[props.rowKey];
  return v == null ? String(idx) : String(v);
}

const selectedSet = computed(() => new Set(props.selected ?? []));

const selectableRows = computed(() =>
  props.selectable
    ? props.rows.filter((r) => (props.isSelectable ? props.isSelectable(r) : true))
    : [],
);
const selectableKeys = computed(() =>
  selectableRows.value.map((r, i) => getRowKey(r, i)),
);
const selectedSelectableCount = computed(
  () => selectableKeys.value.filter((k) => selectedSet.value.has(k)).length,
);
const allSelectableSelected = computed(
  () => selectableKeys.value.length > 0
     && selectedSelectableCount.value === selectableKeys.value.length,
);
const someSelectableSelected = computed(
  () => selectedSelectableCount.value > 0 && !allSelectableSelected.value,
);

function toggleRow(row: Row, idx: number, ev: Event) {
  ev.stopPropagation();
  const key = getRowKey(row, idx);
  const next = new Set(selectedSet.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  emit('update:selected', Array.from(next));
}

function toggleAll() {
  if (allSelectableSelected.value) {
    const remaining = (props.selected ?? []).filter(
      (k) => !selectableKeys.value.includes(k),
    );
    emit('update:selected', remaining);
  } else {
    const next = new Set(props.selected ?? []);
    for (const k of selectableKeys.value) next.add(k);
    emit('update:selected', Array.from(next));
  }
}

if (typeof window !== 'undefined' && import.meta.env.DEV) {
  watch(
    () => props.selectAllScope,
    (scope) => {
      if (scope === 'all') {
        console.warn(
          '[DataTable] selectAllScope="all" is not implemented in v2; behaviour falls back to "page".',
        );
      }
    },
    { immediate: true },
  );
}

function renderCell(col: Column<Row>, row: Row): string {
  const raw = (row as Record<string, unknown>)[col.key];
  if (col.format) return col.format(raw, row);
  if (raw == null || raw === '') return '—';
  return String(raw);
}

function onHeaderClick(col: Column<Row>): void {
  if (!col.sortable) return;
  const isCurrent = props.sortBy === col.key;
  const dir: 'asc' | 'desc' = isCurrent && props.sortDir === 'asc' ? 'desc' : 'asc';
  emit('update:sort', { by: col.key, dir });
}
</script>
