<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Container, Pencil, Plus, Snowflake, AlertTriangle, Beaker, Box } from 'lucide-vue-next';
import { useIsoCode } from '../composables/use.iso-code';
import { useContainerArchetype } from '../composables/use.container-archetype';
import { useAuthStore } from '../stores/authStore';
import type { IsoContainerCode, IsoTypeGroup } from '../types/iso-code';

const { t } = useI18n();
const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasRole('ROLE_ADMIN'));

const { isoCodes, getAll, create, update, remove } = useIsoCode();
const { archetypes, getAll: getArchetypes } = useContainerArchetype();

const search = ref('');
const filterTypeGroup = ref<'' | IsoTypeGroup>('');
const filterArchetypeId = ref<string>('');
const includeInactive = ref(false);

const showModal = ref(false);
const editing = ref<IsoContainerCode | null>(null);
const modalIsCreate = ref(false);

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return isoCodes.value.filter((c) => {
    if (q && !c.code.toLowerCase().includes(q) && !c.description.toLowerCase().includes(q)) return false;
    if (filterTypeGroup.value && c.typeGroup !== filterTypeGroup.value) return false;
    if (filterArchetypeId.value && c.archetypeId !== filterArchetypeId.value) return false;
    return true;
  });
});

const archetypeName = (id: string | null) => {
  if (!id) return '—';
  const a = archetypes.value.find((x) => x.id === id);
  return a?.code ?? '—';
};

const onAddCustom = () => {
  modalIsCreate.value = true;
  editing.value = {
    code: '',
    description: '',
    lengthFt: 20,
    heightFt: 8.5,
    typeGroup: 'G',
    isReefer: false,
    isHazmatCapable: false,
    isTank: false,
    isOpenTop: false,
    isStandard: false,
    isActive: true,
    archetypeId: null,
    tareKg: null,
    maxPayloadKg: null,
    maxGrossKg: null,
  };
  showModal.value = true;
};

const onEdit = (c: IsoContainerCode) => {
  modalIsCreate.value = false;
  editing.value = { ...c };
  showModal.value = true;
};

const onSave = async () => {
  if (!editing.value) return;
  if (modalIsCreate.value) {
    await create(editing.value);
  } else {
    await update(editing.value.code, editing.value);
  }
  showModal.value = false;
  editing.value = null;
  await getAll(includeInactive.value);
};

const onDelete = async (c: IsoContainerCode) => {
  if (c.isStandard) return;
  await remove(c.code);
  await getAll(includeInactive.value);
};

const onIncludeInactiveChange = async () => {
  await getAll(includeInactive.value);
};

onMounted(async () => {
  await getAll(includeInactive.value);
  await getArchetypes();
});
</script>

<template>
  <div class="bg-white shadow rounded-lg">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-200">
      <div class="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ t('isoCodes.title', { count: filtered.length }) }}
        </h2>
        <div class="flex flex-wrap items-center gap-2">
          <input
            v-model="search"
            data-test="iso-search"
            type="search"
            :placeholder="t('isoCodes.placeholder.search')"
            class="w-full sm:w-48 px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
          <select
            v-model="filterTypeGroup"
            data-test="iso-filter-typegroup"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
          >
            <option value="">{{ t('isoCodes.filter.allTypeGroups') }}</option>
            <option value="G">G — General</option>
            <option value="R">R — Reefer</option>
            <option value="H">H — Insulated</option>
            <option value="U">U — Open Top</option>
            <option value="T">T — Tank</option>
            <option value="P">P — Flat Rack</option>
            <option value="V">V — Ventilated</option>
            <option value="B">B — Bulk</option>
            <option value="S">S — Named Cargo</option>
          </select>
          <select
            v-model="filterArchetypeId"
            data-test="iso-filter-archetype"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
          >
            <option value="">{{ t('isoCodes.filter.allArchetypes') }}</option>
            <option v-for="a in archetypes" :key="a.id" :value="a.id">{{ a.code }}</option>
          </select>
          <label class="inline-flex items-center text-sm gap-1.5">
            <input
              v-model="includeInactive"
              data-test="iso-include-inactive"
              type="checkbox"
              @change="onIncludeInactiveChange"
            />
            {{ t('isoCodes.filter.includeInactive') }}
          </label>
          <button
            v-if="isAdmin"
            @click="onAddCustom"
            data-test="iso-add-custom"
            :title="t('isoCodes.button.addCustom')"
            class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
          >
            <Plus class="h-4 w-4 sm:mr-2" />
            <span class="hidden sm:inline">{{ t('isoCodes.button.addCustom') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.code') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.description') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.dimensions') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.typeGroup') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.archetype') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.flags') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.weights') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('isoCodes.column.status') }}</th>
            <th class="sticky right-0 bg-gray-50 px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]">{{ t('isoCodes.column.actions') }}</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="c in filtered" :key="c.code" :data-test="`iso-row-${c.code}`" class="group hover:bg-gray-50">
            <td class="px-4 py-3 whitespace-nowrap font-mono text-sm">{{ c.code }}</td>
            <td class="px-4 py-3 text-sm text-gray-900">{{ c.description }}</td>
            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-700">{{ c.lengthFt }}' × {{ c.heightFt }}'</td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">{{ c.typeGroup }}</td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <span v-if="c.archetypeId" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">{{ archetypeName(c.archetypeId) }}</span>
              <span v-else class="text-gray-400">—</span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <div class="flex items-center gap-1.5">
                <Snowflake v-if="c.isReefer" class="h-4 w-4 text-cyan-600" :title="t('isoCodes.flag.reefer')" />
                <AlertTriangle v-if="c.isHazmatCapable" class="h-4 w-4 text-amber-600" :title="t('isoCodes.flag.hazmat')" />
                <Beaker v-if="c.isTank" class="h-4 w-4 text-purple-600" :title="t('isoCodes.flag.tank')" />
                <Box v-if="c.isOpenTop" class="h-4 w-4 text-gray-600" :title="t('isoCodes.flag.openTop')" />
              </div>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-700">
              <span v-if="c.tareKg != null && c.maxGrossKg != null">{{ c.tareKg }} / {{ c.maxGrossKg }} kg</span>
              <span v-else class="text-gray-400">—</span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <span :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border',
                c.isStandard ? 'bg-gray-100 text-gray-700 border-gray-200' : 'bg-indigo-50 text-indigo-700 border-indigo-200']">
                {{ c.isStandard ? t('isoCodes.status.standard') : t('isoCodes.status.custom') }}
              </span>
              <span v-if="!c.isActive" class="ml-1 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-700 border border-red-200">
                {{ t('isoCodes.status.inactive') }}
              </span>
            </td>
            <td class="sticky right-0 bg-white group-hover:bg-gray-50 transition-colors duration-150 px-4 py-3 whitespace-nowrap text-right text-sm font-medium shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]">
              <div class="flex items-center justify-end space-x-2">
                <button
                  v-if="isAdmin"
                  @click="onEdit(c)"
                  :data-test="`iso-edit-${c.code}`"
                  class="text-blue-600 hover:text-blue-900 p-1 rounded hover:bg-blue-50"
                  :title="t('isoCodes.button.edit')"
                >
                  <Pencil class="h-4 w-4" />
                </button>
                <button
                  v-if="isAdmin"
                  @click="onDelete(c)"
                  :disabled="c.isStandard"
                  :data-test="`iso-delete-${c.code}`"
                  :class="[
                    'p-1 rounded',
                    c.isStandard ? 'text-gray-300 cursor-not-allowed' : 'text-red-600 hover:text-red-900 hover:bg-red-50'
                  ]"
                  :title="c.isStandard ? t('isoCodes.tooltip.cannotDeleteStandard') : t('isoCodes.button.delete')"
                >
                  <Container class="h-4 w-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Edit modal -->
    <div
      v-if="showModal && editing"
      data-test="iso-modal"
      class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
    >
      <div class="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div class="px-4 py-3 border-b border-gray-200 flex justify-between items-center">
          <h3 class="text-lg font-medium text-gray-900">
            {{ modalIsCreate ? t('isoCodes.modal.createTitle') : t('isoCodes.modal.editTitle') }}
            <span data-test="iso-modal-code" class="font-mono ml-2">{{ editing.code }}</span>
          </h3>
        </div>
        <div class="px-4 py-3 space-y-3">
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.code') }}</label>
            <input
              v-model="editing.code"
              :readonly="!modalIsCreate"
              :class="['mt-1 block w-full rounded-md shadow-sm text-sm border px-3 py-2',
                       !modalIsCreate ? 'bg-gray-100 border-gray-200' : 'border-gray-300']"
              maxlength="4"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.description') }}</label>
            <textarea v-model="editing.description" rows="2" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.lengthFt') }}</label>
              <input
                v-model.number="editing.lengthFt"
                type="number"
                :readonly="editing.isStandard"
                :class="['mt-1 block w-full rounded-md shadow-sm text-sm border px-3 py-2',
                         editing.isStandard ? 'bg-gray-100 border-gray-200' : 'border-gray-300']"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.heightFt') }}</label>
              <input
                v-model.number="editing.heightFt"
                type="number"
                step="0.1"
                :readonly="editing.isStandard"
                :class="['mt-1 block w-full rounded-md shadow-sm text-sm border px-3 py-2',
                         editing.isStandard ? 'bg-gray-100 border-gray-200' : 'border-gray-300']"
              />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.typeGroup') }}</label>
            <select
              v-model="editing.typeGroup"
              :disabled="editing.isStandard"
              :class="['mt-1 block w-full rounded-md shadow-sm text-sm border px-3 py-2',
                       editing.isStandard ? 'bg-gray-100 border-gray-200' : 'border-gray-300']"
            >
              <option value="G">G</option><option value="R">R</option><option value="H">H</option>
              <option value="U">U</option><option value="T">T</option><option value="P">P</option>
              <option value="V">V</option><option value="B">B</option><option value="S">S</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.archetype') }}</label>
            <select v-model="editing.archetypeId" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2">
              <option :value="null">{{ t('isoCodes.field.archetypeNone') }}</option>
              <option v-for="a in archetypes" :key="a.id" :value="a.id">{{ a.code }} — {{ a.name }}</option>
            </select>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <label class="inline-flex items-center text-sm gap-2">
              <input v-model="editing.isReefer" type="checkbox" /> {{ t('isoCodes.flag.reefer') }}
            </label>
            <label class="inline-flex items-center text-sm gap-2">
              <input v-model="editing.isHazmatCapable" type="checkbox" /> {{ t('isoCodes.flag.hazmat') }}
            </label>
            <label class="inline-flex items-center text-sm gap-2">
              <input v-model="editing.isTank" type="checkbox" /> {{ t('isoCodes.flag.tank') }}
            </label>
            <label class="inline-flex items-center text-sm gap-2">
              <input v-model="editing.isOpenTop" type="checkbox" /> {{ t('isoCodes.flag.openTop') }}
            </label>
          </div>
          <label class="inline-flex items-center text-sm gap-2">
            <input v-model="editing.isActive" type="checkbox" /> {{ t('isoCodes.field.isActive') }}
          </label>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.tareKg') }}</label>
              <input v-model.number="editing.tareKg" type="number" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.maxPayloadKg') }}</label>
              <input v-model.number="editing.maxPayloadKg" type="number" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">{{ t('isoCodes.field.maxGrossKg') }}</label>
              <input v-model.number="editing.maxGrossKg" type="number" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2" />
            </div>
          </div>
        </div>
        <div class="px-4 py-3 border-t border-gray-200 flex justify-end gap-2">
          <button @click="showModal = false" data-test="iso-modal-cancel" class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">{{ t('common.cancel') }}</button>
          <button @click="onSave" data-test="iso-modal-save" class="px-4 py-2 border border-transparent rounded-md text-sm font-medium text-white bg-blue-600 hover:bg-blue-700">{{ t('common.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
