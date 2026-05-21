<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { useI18n } from 'vue-i18n';

import { v4 as uuidv4 } from "uuid";
import {
  Search,
  Filter,
  Save,
  Download,
  Trash2,
  Plus,
  X,
  ChevronDown,
  ChevronUp,
  BookmarkPlus,
  Bookmark,
  AlertCircle,
} from "lucide-vue-next";

const { t } = useI18n();

interface FilterCondition {
  id: string;
  field: string;
  operator: string;
  value: any;
  logicalOperator?: "AND" | "OR";
}

interface FilterGroup {
  id: string;
  conditions: FilterCondition[];
  logicalOperator: "AND" | "OR";
}

interface SavedFilter {
  id: string;
  name: string;
  type: string;
  groups: FilterGroup[];
}

interface FilterField {
  name: string;
  label: string;
  type: "text" | "number" | "date" | "select" | "multiselect";
  operators: string[];
  options?: { label: string; value: any }[];
}

const props = defineProps<{
  type: "contracts" | "items" | "invoices" | "vessels" | "users" | "rates";
}>();

const emit = defineEmits<{
  (e: "filter", filters: FilterGroup[]): void;
}>();

// Filter fields configuration based on type
const filterFields = computed<Record<string, FilterField>>(() => {
  switch (props.type) {
    case "contracts":
      return {
        name: {
          name: "name",
          label: t('advancedFilter.field.contractName'),
          type: "text",
          operators: ["contains", "equals", "starts_with", "ends_with"],
        },
        status: {
          name: "status",
          label: t('advancedFilter.field.status'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.active'), value: "Active" },
            { label: t('advancedFilter.option.inactive'), value: "Inactive" },
            { label: t('advancedFilter.option.draft'), value: "Draft" },
          ],
        },
        startDate: {
          name: "startDate",
          label: t('advancedFilter.field.startDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
        endDate: {
          name: "endDate",
          label: t('advancedFilter.field.endDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
        contractValue: {
          name: "contractValue",
          label: t('advancedFilter.field.contractValue'),
          type: "number",
          operators: ["equals", "greater_than", "less_than", "between"],
        },
      };
    case "items":
      return {
        itemNumber: {
          name: "itemNumber",
          label: t('advancedFilter.field.itemNumber'),
          type: "text",
          operators: ["contains", "equals", "starts_with"],
        },
        itemType: {
          name: "itemType",
          label: t('advancedFilter.field.itemType'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.container'), value: "container" },
            { label: t('advancedFilter.option.breakbulk'), value: "breakbulk" },
            { label: t('advancedFilter.option.vehicle'), value: "vehicle" },
          ],
        },
        position: {
          name: "position",
          label: t('advancedFilter.field.position'),
          type: "text",
          operators: ["contains", "equals"],
        },
        ownerId: {
          name: "ownerId",
          label: t('advancedFilter.field.ownerId'),
          type: "text",
          operators: ["contains", "equals"],
        },
      };
    case "invoices":
      return {
        draftNumber: {
          name: "draftNumber",
          label: t('advancedFilter.field.draftNumber'),
          type: "text",
          operators: ["contains", "equals", "starts_with"],
        },
        finalNumber: {
          name: "finalNumber",
          label: t('advancedFilter.field.finalNumber'),
          type: "text",
          operators: ["contains", "equals", "starts_with"],
        },
        status: {
          name: "status",
          label: t('advancedFilter.field.status'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.draft'), value: "DRAFT" },
            { label: t('advancedFilter.option.final'), value: "FINAL" },
            { label: t('advancedFilter.option.cancelled'), value: "CANCELLED" },
          ],
        },
        customerName: {
          name: "customerName",
          label: t('advancedFilter.field.customerName'),
          type: "text",
          operators: ["contains", "equals"],
        },
        amount: {
          name: "amount",
          label: t('advancedFilter.field.amount'),
          type: "number",
          operators: ["equals", "greater_than", "less_than", "between"],
        },
        createdDate: {
          name: "createdDate",
          label: t('advancedFilter.field.createdDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
      };
    case "vessels":
      return {
        vesselName: {
          name: "vesselName",
          label: t('advancedFilter.field.vesselName'),
          type: "text",
          operators: ["contains", "equals", "starts_with"],
        },
        visitReference: {
          name: "visitReference",
          label: t('advancedFilter.field.visitReference'),
          type: "text",
          operators: ["contains", "equals"],
        },
        phase: {
          name: "phase",
          label: t('advancedFilter.field.phase'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.created'), value: "Created" },
            { label: t('advancedFilter.option.active'), value: "Active" },
            { label: t('advancedFilter.option.completed'), value: "Completed" },
            { label: t('advancedFilter.option.canceled'), value: "Canceled" },
          ],
        },
        service: {
          name: "service",
          label: t('advancedFilter.field.service'),
          type: "text",
          operators: ["contains", "equals"],
        },
        eta: {
          name: "eta",
          label: t('advancedFilter.field.eta'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
        etd: {
          name: "etd",
          label: t('advancedFilter.field.etd'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
      };
    case "users":
      return {
        fullName: {
          name: "fullName",
          label: t('advancedFilter.field.fullName'),
          type: "text",
          operators: ["contains", "equals", "starts_with"],
        },
        companyName: {
          name: "companyName",
          label: t('advancedFilter.field.companyName'),
          type: "text",
          operators: ["contains", "equals"],
        },
        accessType: {
          name: "accessType",
          label: t('advancedFilter.field.accessType'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.viewOnly'), value: "View Only" },
            { label: t('advancedFilter.option.dataEntry'), value: "Data Entry" },
            { label: t('advancedFilter.option.fullAccess'), value: "Full Access" },
          ],
        },
        status: {
          name: "status",
          label: t('advancedFilter.field.status'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.active'), value: "Active" },
            { label: t('advancedFilter.option.inactive'), value: "Inactive" },
            { label: t('advancedFilter.option.pending'), value: "Pending" },
          ],
        },
        createdAt: {
          name: "createdAt",
          label: t('advancedFilter.field.createdDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
        expiresAt: {
          name: "expiresAt",
          label: t('advancedFilter.field.expiryDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
      };
    case "rates":
      return {
        rateType: {
          name: "rateType",
          label: t('advancedFilter.field.rateType'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.quantity'), value: "Quantity" },
            { label: t('advancedFilter.option.date'), value: "Date" },
          ],
        },
        amount: {
          name: "amount",
          label: t('advancedFilter.field.amount'),
          type: "number",
          operators: ["equals", "greater_than", "less_than", "between"],
        },
        currency: {
          name: "currency",
          label: t('advancedFilter.field.currency'),
          type: "select",
          operators: ["equals", "not_equals"],
          options: [
            { label: t('advancedFilter.option.usd'), value: "USD" },
            { label: t('advancedFilter.option.eur'), value: "EUR" },
            { label: t('advancedFilter.option.gbp'), value: "GBP" },
          ],
        },
        effectiveDate: {
          name: "effectiveDate",
          label: t('advancedFilter.field.effectiveDate'),
          type: "date",
          operators: ["equals", "before", "after", "between"],
        },
      };
    default:
      return {};
  }
});

const isExpanded = ref(false);
const filterGroups = ref<FilterGroup[]>([
  {
    id: uuidv4(),

    conditions: [],
    logicalOperator: "AND",
  },
]);
const savedFilters = ref<SavedFilter[]>([]);
const showSaveFilterModal = ref(false);
const newFilterName = ref("");
const activePreset = ref<string | null>(null);

const operatorLabels = computed<Record<string, string>>(() => ({
  contains: t('advancedFilter.operator.contains'),
  equals: t('advancedFilter.operator.equals'),
  not_equals: t('advancedFilter.operator.notEquals'),
  starts_with: t('advancedFilter.operator.startsWith'),
  ends_with: t('advancedFilter.operator.endsWith'),
  greater_than: t('advancedFilter.operator.greaterThan'),
  less_than: t('advancedFilter.operator.lessThan'),
  between: t('advancedFilter.operator.between'),
  before: t('advancedFilter.operator.before'),
  after: t('advancedFilter.operator.after'),
}));

const addFilterGroup = () => {
  filterGroups.value.push({
    id: uuidv4(),
    conditions: [],
    logicalOperator: "AND",
  });
};

const addCondition = (groupId: string) => {
  const group = filterGroups.value.find((g) => g.id === groupId);
  if (group) {
    group.conditions.push({
      id: uuidv4(),
      field: Object.keys(filterFields.value)[0],
      operator: filterFields.value[Object.keys(filterFields.value)[0]].operators[0],
      value: "",
      logicalOperator: group.conditions.length > 0 ? "AND" : undefined,
    });
  }
};

const removeCondition = (groupId: string, conditionId: string) => {
  const group = filterGroups.value.find((g) => g.id === groupId);
  if (group) {
    group.conditions = group.conditions.filter((c) => c.id !== conditionId);
  }
};

const removeGroup = (groupId: string) => {
  filterGroups.value = filterGroups.value.filter((g) => g.id !== groupId);
};

const saveFilter = () => {
  if (!newFilterName.value) return;

  savedFilters.value.push({
    id: uuidv4(),
    name: newFilterName.value,
    type: props.type,
    groups: JSON.parse(JSON.stringify(filterGroups.value)),
  });

  showSaveFilterModal.value = false;
  newFilterName.value = "";
};

const loadFilter = (filter: SavedFilter) => {
  filterGroups.value = JSON.parse(JSON.stringify(filter.groups));
  activePreset.value = filter.id;
};

const deleteFilter = (filterId: string) => {
  savedFilters.value = savedFilters.value.filter((f) => f.id !== filterId);
  if (activePreset.value === filterId) {
    activePreset.value = null;
  }
};

const applyFilters = () => {
  emit("filter", filterGroups.value);
};

const clearFilters = () => {
  filterGroups.value = [
    {
      id: uuidv4(),
      conditions: [],
      logicalOperator: "AND",
    },
  ];
  activePreset.value = null;
  emit("filter", []);
};

watch(
  filterGroups,
  () => {
    if (activePreset.value) {
      const currentFilter = JSON.stringify(filterGroups.value);
      const savedFilter = savedFilters.value.find((f) => f.id === activePreset.value);
      if (savedFilter && JSON.stringify(savedFilter.groups) !== currentFilter) {
        activePreset.value = null;
      }
    }
  },
  { deep: true }
);
</script>

<template>
  <div class="bg-white shadow rounded-lg">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-200">
      <button
        @click="isExpanded = !isExpanded"
        class="flex items-center text-sm text-gray-700 hover:text-gray-900"
      >
        <Filter class="h-4 w-4 mr-2" />
        {{ t('advancedFilter.header.title') }}
        <component :is="isExpanded ? ChevronUp : ChevronDown" class="h-4 w-4 ml-2" />
      </button>
    </div>

    <!-- Filter Content -->
    <div v-show="isExpanded" class="p-4">
      <!-- Saved Filters -->
      <div class="mb-4">
        <div class="flex justify-between items-center mb-2">
          <h3 class="text-sm font-medium text-gray-700">{{ t('advancedFilter.savedFilters.title') }}</h3>
          <button
            v-if="filterGroups[0].conditions.length > 0"
            @click="showSaveFilterModal = true"
            class="inline-flex items-center text-sm text-blue-600 hover:text-blue-800"
          >
            <BookmarkPlus class="h-4 w-4 mr-1" />
            {{ t('advancedFilter.savedFilters.saveCurrent') }}
          </button>
        </div>
        <div class="space-y-2">
          <div v-if="savedFilters.length === 0" class="text-sm text-gray-500">
            {{ t('advancedFilter.savedFilters.empty') }}
          </div>
          <div
            v-for="filter in savedFilters"
            :key="filter.id"
            class="flex items-center justify-between p-2 bg-gray-50 rounded-md"
          >
            <button
              @click="loadFilter(filter)"
              :class="[
                'flex items-center text-sm',
                activePreset === filter.id
                  ? 'text-blue-600'
                  : 'text-gray-700 hover:text-gray-900',
              ]"
            >
              <Bookmark class="h-4 w-4 mr-2" />
              {{ filter.name }}
            </button>
            <button
              @click="deleteFilter(filter.id)"
              class="text-gray-400 hover:text-red-600"
            >
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      <!-- Filter Groups -->
      <div class="space-y-4">
        <div
          v-for="group in filterGroups"
          :key="group.id"
          class="p-4 bg-gray-50 rounded-lg"
        >
          <!-- Group Header -->
          <div class="flex justify-between items-center mb-4">
            <div class="flex items-center space-x-4">
              <select
                v-model="group.logicalOperator"
                class="text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="AND">{{ t('advancedFilter.logical.and') }}</option>
                <option value="OR">{{ t('advancedFilter.logical.or') }}</option>
              </select>
              <button
                v-if="filterGroups.length > 1"
                @click="removeGroup(group.id)"
                class="text-red-600 hover:text-red-800"
              >
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- Conditions -->
          <div class="space-y-4">
            <div
              v-for="condition in group.conditions"
              :key="condition.id"
              class="grid grid-cols-12 gap-4 items-start"
            >
              <!-- Logical Operator -->
              <div v-if="condition.logicalOperator" class="col-span-1">
                <select
                  v-model="condition.logicalOperator"
                  class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                >
                  <option value="AND">{{ t('advancedFilter.logical.and') }}</option>
                  <option value="OR">{{ t('advancedFilter.logical.or') }}</option>
                </select>
              </div>
              <div :class="condition.logicalOperator ? 'col-span-11' : 'col-span-12'">
                <div class="flex items-start space-x-4">
                  <!-- Field -->
                  <div class="w-1/4">
                    <select
                      v-model="condition.field"
                      class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                    >
                      <option
                        v-for="(field, key) in filterFields"
                        :key="key"
                        :value="key"
                      >
                        {{ field.label }}
                      </option>
                    </select>
                  </div>

                  <!-- Operator -->
                  <div class="w-1/4">
                    <select
                      v-model="condition.operator"
                      class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                    >
                      <option
                        v-for="operator in filterFields[condition.field].operators"
                        :key="operator"
                        :value="operator"
                      >
                        {{ operatorLabels[operator] }}
                      </option>
                    </select>
                  </div>

                  <!-- Value -->
                  <div class="flex-1">
                    <template v-if="filterFields[condition.field].type === 'select'">
                      <select
                        v-model="condition.value"
                        class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option
                          v-for="option in filterFields[condition.field].options"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </template>

                    <template
                      v-else-if="filterFields[condition.field].type === 'multiselect'"
                    >
                      <select
                        v-model="condition.value"
                        multiple
                        class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                      >
                        <option
                          v-for="option in filterFields[condition.field].options"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </option>
                      </select>
                    </template>

                    <template v-else-if="filterFields[condition.field].type === 'date'">
                      <div class="flex space-x-2">
                        <input
                          v-model="condition.value"
                          type="date"
                          class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                        />
                        <input
                          v-if="condition.operator === 'between'"
                          v-model="condition.value2"
                          type="date"
                          class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                        />
                      </div>
                    </template>

                    <template v-else-if="filterFields[condition.field].type === 'number'">
                      <div class="flex space-x-2">
                        <input
                          v-model.number="condition.value"
                          type="number"
                          class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                        />
                        <input
                          v-if="condition.operator === 'between'"
                          v-model.number="condition.value2"
                          type="number"
                          class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                        />
                      </div>
                    </template>

                    <template v-else>
                      <input
                        v-model="condition.value"
                        type="text"
                        class="block w-full text-sm border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                      />
                    </template>
                  </div>

                  <!-- Remove Condition -->
                  <button
                    @click="removeCondition(group.id, condition.id)"
                    class="text-red-600 hover:text-red-800"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>

            <!-- Add Condition -->
            <button
              @click="addCondition(group.id)"
              class="inline-flex items-center text-sm text-blue-600 hover:text-blue-800"
            >
              <Plus class="h-4 w-4 mr-1" />
              {{ t('advancedFilter.action.addCondition') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Add Group -->
      <div class="mt-4">
        <button
          @click="addFilterGroup"
          class="inline-flex items-center text-sm text-blue-600 hover:text-blue-800"
        >
          <Plus class="h-4 w-4 mr-1" />
          {{ t('advancedFilter.action.addGroup') }}
        </button>
      </div>

      <!-- Actions -->
      <div class="mt-6 flex justify-end space-x-4">
        <button
          @click="clearFilters"
          class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          {{ t('advancedFilter.action.clear') }}
        </button>
        <button
          @click="applyFilters"
          class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
        >
          {{ t('advancedFilter.action.apply') }}
        </button>
      </div>
    </div>

    <!-- Save Filter Modal -->
    <Teleport to="body">
      <div
        v-if="showSaveFilterModal"
        class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
      >
        <div class="bg-white rounded-lg p-6 max-w-md w-full">
          <div class="flex justify-between items-center mb-4">
            <h3 class="text-lg font-medium text-gray-900">{{ t('advancedFilter.modal.title') }}</h3>
            <button
              @click="showSaveFilterModal = false"
              class="text-gray-400 hover:text-gray-500"
            >
              <X class="h-6 w-6" />
            </button>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700"> {{ t('advancedFilter.modal.nameLabel') }} </label>
              <input
                v-model="newFilterName"
                type="text"
                class="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:ring-blue-500 focus:border-blue-500"
                :placeholder="t('advancedFilter.modal.namePlaceholder')"
              />
            </div>

            <div class="flex justify-end space-x-3">
              <button
                @click="showSaveFilterModal = false"
                class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                {{ t('advancedFilter.modal.cancel') }}
              </button>
              <button
                @click="saveFilter"
                class="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
              >
                {{ t('advancedFilter.modal.save') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
