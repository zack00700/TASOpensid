<script setup lang="ts">
import { ref, computed, inject, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Plus, Trash2, Edit, X, Check } from 'lucide-vue-next';
import type { CalcFilter, FilterTarget, ValueType, FilterOp } from '../types/calc-filter';

const { t } = useI18n();

interface FilterField {
  field: string;
  label: string;
  valueType: ValueType;
}

const props = defineProps<{ modelValue: CalcFilter[] }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: CalcFilter[]): void }>();

const $axios = inject<any>('$axios');

const filters = ref<CalcFilter[]>([]);
watch(
  () => props.modelValue,
  (val) => {
    filters.value = [...val];
  },
  { immediate: true }
);

const filterableFields = ref<Record<FilterTarget, FilterField[]>>({
  ITEM: [],
  BILL_OF_LADING: []
});

onMounted(async () => {
  try {
    if (!$axios) throw new Error('HTTP client not provided');
    const { data } = await $axios.get('/contract/filterable-fields');
    const toArray = (v: unknown): FilterField[] => (Array.isArray(v) ? v : []);
    filterableFields.value.ITEM = toArray(
      data.ITEM || data.item || data.items || data.Item
    );
    filterableFields.value.BILL_OF_LADING = toArray(
      data.BILL_OF_LADING || data.billOfLading || data.BOL || data.bol
    );
  } catch (err) {
    console.error('Failed to fetch filterable fields', err);
    // Fallback sample fields
    filterableFields.value.ITEM = [
      { field: 'size', label: 'size', valueType: 'INT' },
      { field: 'ownerCode', label: 'ownerCode', valueType: 'STRING' }
    ];
    filterableFields.value.BILL_OF_LADING = [
      { field: 'eta', label: 'eta', valueType: 'DATE' },
      { field: 'bolNumber', label: 'bolNumber', valueType: 'STRING' }
    ];
  }
});

const operatorOptions = computed<Record<ValueType, { value: FilterOp; label: string }[]>>(() => ({
  STRING: [{ value: 'EQ', label: t('filterBuilder.operator.equals') }],
  INT: [
    { value: 'EQ', label: t('filterBuilder.operator.equals') },
    { value: 'LT', label: t('filterBuilder.operator.lessThan') },
    { value: 'GT', label: t('filterBuilder.operator.greaterThan') },
    { value: 'BETWEEN', label: t('filterBuilder.operator.between') }
  ],
  DATE: [
    { value: 'EQ', label: t('filterBuilder.operator.equals') },
    { value: 'LT', label: t('filterBuilder.operator.lessThan') },
    { value: 'GT', label: t('filterBuilder.operator.greaterThan') },
    { value: 'BETWEEN', label: t('filterBuilder.operator.between') }
  ]
}));

const editingIndex = ref<number | null>(null);
const draft = ref<CalcFilter | null>(null);

const startAdd = () => {
  editingIndex.value = filters.value.length;
  const firstField = filterableFields.value?.ITEM?.[0];
  draft.value = {
    target: 'ITEM',
    field: firstField ? firstField.field : '',
    valueType: firstField ? firstField.valueType : 'STRING',
    op: 'EQ',
    value: ''
  };
};

const startEdit = (idx: number) => {
  editingIndex.value = idx;
  draft.value = { ...filters.value[idx] };
};

const cancelEdit = () => {
  editingIndex.value = null;
  draft.value = null;
};

const onTargetChange = () => {
  if (!draft.value) return;
  const list = filterableFields.value?.[draft.value.target] || [];
  draft.value.field = list[0]?.field || '';
  onFieldChange();
};

const onFieldChange = () => {
  if (!draft.value) return;
  const list = filterableFields.value?.[draft.value.target] || [];
  const field = list.find((f) => f.field === draft.value!.field);
  draft.value.valueType = field?.valueType || 'STRING';
  draft.value.op = operatorOptions.value[draft.value.valueType][0].value;
  draft.value.value = '';
  draft.value.valueTo = undefined;
};

const saveDraft = () => {
  if (!draft.value) return;
  if (draft.value.op === 'BETWEEN') {
    if (!draft.value.value || !draft.value.valueTo) {
      return;
    }
    if (draft.value.valueType !== 'STRING') {
      if (draft.value.value > draft.value.valueTo) {
        return;
      }
    }
  }

  if (editingIndex.value === filters.value.length) {
    filters.value.push({ ...draft.value });
  } else if (editingIndex.value !== null) {
    filters.value[editingIndex.value] = { ...draft.value };
  }
  emit('update:modelValue', filters.value);
  cancelEdit();
};

const removeFilter = (idx: number) => {
  filters.value.splice(idx, 1);
  emit('update:modelValue', filters.value);
};

const chipLabel = (f: CalcFilter) => {
  const targetLabel = f.target === 'ITEM' ? t('filterBuilder.chip.item') : t('filterBuilder.chip.bol');
  const parts = [targetLabel, f.field, f.op];
  if (f.op === 'BETWEEN') {
    parts.push(`${f.value} … ${f.valueTo}`);
  } else {
    parts.push(f.value);
  }
  if (f.includeNull) parts.push(t('filterBuilder.chip.includeNull'));
  return parts.join(' • ');
};
</script>

<template>
  <div>
    <div v-if="editingIndex !== null" class="p-4 mb-4 bg-gray-50 rounded-lg">
      <div class="grid grid-cols-5 gap-4 items-end">
        <!-- Target -->
        <div>
          <label class="block text-sm font-medium text-gray-700">{{ t('filterBuilder.label.target') }}</label>
          <select v-model="draft!.target" @change="onTargetChange" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500">
            <option value="ITEM">{{ t('filterBuilder.target.item') }}</option>
            <option value="BILL_OF_LADING">{{ t('filterBuilder.target.billOfLading') }}</option>
          </select>
        </div>
        <!-- Field -->
        <div>
          <label class="block text-sm font-medium text-gray-700">{{ t('filterBuilder.label.field') }}</label>
          <select v-model="draft!.field" @change="onFieldChange" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500">
            <template v-if="(filterableFields?.[draft!.target] || []).length">
              <option v-for="field in filterableFields?.[draft!.target] || []" :key="field.field" :value="field.field">
                {{ field.label }}
              </option>
            </template>
            <option v-else disabled value="">{{ t('filterBuilder.option.noFieldsAvailable') }}</option>
          </select>
        </div>
        <!-- Operator -->
        <div>
          <label class="block text-sm font-medium text-gray-700">{{ t('filterBuilder.label.operator') }}</label>
          <select v-model="draft!.op" class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500">
            <option v-for="op in operatorOptions[draft!.valueType]" :key="op.value" :value="op.value">
              {{ op.label }}
            </option>
          </select>
        </div>
        <!-- Value -->
        <div class="col-span-2">
          <label class="block text-sm font-medium text-gray-700">{{ t('filterBuilder.label.value') }}</label>
          <template v-if="draft!.op === 'BETWEEN'">
            <div class="flex space-x-2">
              <input
                v-model="draft!.value"
                :type="draft!.valueType === 'DATE' ? 'date' : 'number'"
                class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
              />
              <input
                v-model="draft!.valueTo"
                :type="draft!.valueType === 'DATE' ? 'date' : 'number'"
                class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
              />
            </div>
          </template>
          <template v-else>
            <input
              v-model="draft!.value"
              :type="draft!.valueType === 'DATE' ? 'date' : draft!.valueType === 'INT' ? 'number' : 'text'"
              class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500"
            />
          </template>
          <div v-if="draft!.target === 'BILL_OF_LADING'" class="mt-2 flex items-center space-x-2">
            <input
              id="include-null"
              type="checkbox"
              v-model="draft!.includeNull"
              class="rounded border-gray-300 text-blue-600 shadow-sm focus:border-blue-500 focus:ring-blue-500"
            />
            <label for="include-null" class="text-sm text-gray-700" :title="t('filterBuilder.includeNull.tooltip')">
              {{ t('filterBuilder.includeNull.label') }}
            </label>
          </div>
        </div>
      </div>
      <div class="mt-4 flex space-x-2">
        <button type="button" @click="saveDraft" class="inline-flex items-center px-3 py-1.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-md">
          <Check class="w-4 h-4 mr-1" /> {{ t('filterBuilder.button.save') }}
        </button>
        <button type="button" @click="cancelEdit" class="inline-flex items-center px-3 py-1.5 text-sm font-medium text-gray-700 bg-gray-200 hover:bg-gray-300 rounded-md">
          <X class="w-4 h-4 mr-1" /> {{ t('common.cancel') }}
        </button>
      </div>
    </div>

    <div v-else class="mb-4">
      <button type="button" @click="startAdd" class="inline-flex items-center px-3 py-2 text-sm font-medium text-blue-600 hover:text-blue-900">
        <Plus class="h-4 w-4 mr-1" />
        {{ t('filterBuilder.button.addFilter') }}
      </button>
    </div>

    <div class="flex flex-wrap gap-2">
      <div
        v-for="(f, i) in filters"
        :key="i"
        v-show="editingIndex !== i"
        class="flex items-center space-x-2 bg-gray-100 px-3 py-1 rounded-full text-sm"
      >
        <span>{{ chipLabel(f) }}</span>
        <button type="button" @click="startEdit(i)" class="text-gray-500 hover:text-gray-700">
          <Edit class="h-4 w-4" />
        </button>
        <button type="button" @click="removeFilter(i)" class="text-red-500 hover:text-red-700">
          <Trash2 class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>
