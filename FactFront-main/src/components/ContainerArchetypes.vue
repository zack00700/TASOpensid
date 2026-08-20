<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Pencil, Plus, Trash2 } from 'lucide-vue-next';
import { useContainerArchetype } from '../composables/use.container-archetype';
import { useAuthStore } from '../stores/authStore';
import type { ContainerArchetype } from '../types/container-archetype';

const { t } = useI18n();
const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasRole('ROLE_ADMIN'));

const { archetypes, getAll, create, update, remove, getIsoCodesFor } = useContainerArchetype();

const search = ref('');
const showModal = ref(false);
const editing = ref<ContainerArchetype | null>(null);
const modalIsCreate = ref(false);
const assignedCount = ref<Record<string, number>>({});

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return archetypes.value;
  return archetypes.value.filter((a) =>
    a.code.toLowerCase().includes(q) ||
    a.name.toLowerCase().includes(q) ||
    (a.description ?? '').toLowerCase().includes(q)
  );
});

const refreshAssignedCounts = async () => {
  const next: Record<string, number> = {};
  await Promise.all(
    archetypes.value.map(async (a) => {
      if (!a.id) return;
      const codes = await getIsoCodesFor(a.id);
      next[a.id] = codes.length;
    })
  );
  assignedCount.value = next;
};

const onAdd = () => {
  modalIsCreate.value = true;
  editing.value = { code: '', name: '', description: '', isActive: true };
  showModal.value = true;
};

const onEdit = (a: ContainerArchetype) => {
  modalIsCreate.value = false;
  editing.value = { ...a };
  showModal.value = true;
};

const onSave = async () => {
  if (!editing.value) return;
  if (modalIsCreate.value) {
    await create(editing.value);
  } else if (editing.value.id) {
    await update(editing.value.id, editing.value);
  }
  showModal.value = false;
  editing.value = null;
  await getAll();
  await refreshAssignedCounts();
};

const onDelete = async (a: ContainerArchetype) => {
  if (!a.id || (assignedCount.value[a.id] ?? 0) > 0) return;
  await remove(a.id);
  await getAll();
  await refreshAssignedCounts();
};

onMounted(async () => {
  await getAll();
  await refreshAssignedCounts();
});
</script>

<template>
  <div class="bg-white shadow rounded-lg">
    <div class="px-4 py-3 border-b border-gray-200">
      <div class="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ t('archetypes.title', { count: filtered.length }) }}
        </h2>
        <div class="flex flex-wrap items-center gap-2">
          <input
            v-model="search"
            data-test="arch-search"
            type="search"
            :placeholder="t('archetypes.placeholder.search')"
            class="w-full sm:w-48 px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
          <button
            v-if="isAdmin"
            @click="onAdd"
            data-test="arch-add"
            :title="t('archetypes.button.add')"
            class="inline-flex items-center px-3 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
          >
            <Plus class="h-4 w-4 sm:mr-2" />
            <span class="hidden sm:inline">{{ t('archetypes.button.add') }}</span>
          </button>
        </div>
      </div>
    </div>

    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('archetypes.column.code') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('archetypes.column.name') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('archetypes.column.description') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('archetypes.column.assigned') }}</th>
            <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{{ t('archetypes.column.status') }}</th>
            <th class="sticky right-0 bg-gray-50 px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]">{{ t('archetypes.column.actions') }}</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="a in filtered" :key="a.id" :data-test="`arch-row-${a.id}`" class="group hover:bg-gray-50">
            <td class="px-4 py-3 whitespace-nowrap font-mono text-sm">{{ a.code }}</td>
            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-900">{{ a.name }}</td>
            <td class="px-4 py-3 text-sm text-gray-700 max-w-xs truncate" :title="a.description">{{ a.description }}</td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <span :data-test="`arch-count-${a.id}`" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200">{{ assignedCount[a.id ?? ''] ?? 0 }}</span>
            </td>
            <td class="px-4 py-3 whitespace-nowrap text-sm">
              <span v-if="a.isActive" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">{{ t('archetypes.status.active') }}</span>
              <span v-else class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-700 border border-red-200">{{ t('archetypes.status.inactive') }}</span>
            </td>
            <td class="sticky right-0 bg-white group-hover:bg-gray-50 transition-colors duration-150 px-4 py-3 whitespace-nowrap text-right text-sm font-medium shadow-[-4px_0_4px_-4px_rgba(0,0,0,0.06)]">
              <div class="flex items-center justify-end space-x-2">
                <button
                  v-if="isAdmin"
                  @click="onEdit(a)"
                  :data-test="`arch-edit-${a.id}`"
                  class="text-blue-600 hover:text-blue-900 p-1 rounded hover:bg-blue-50"
                  :title="t('archetypes.button.edit')"
                >
                  <Pencil class="h-4 w-4" />
                </button>
                <button
                  v-if="isAdmin"
                  @click="onDelete(a)"
                  :disabled="(assignedCount[a.id ?? ''] ?? 0) > 0"
                  :data-test="`arch-delete-${a.id}`"
                  :class="[
                    'p-1 rounded',
                    (assignedCount[a.id ?? ''] ?? 0) > 0 ? 'text-gray-300 cursor-not-allowed' : 'text-red-600 hover:text-red-900 hover:bg-red-50'
                  ]"
                  :title="(assignedCount[a.id ?? ''] ?? 0) > 0 ? t('archetypes.tooltip.cannotDeleteInUse') : t('archetypes.button.delete')"
                >
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-if="showModal && editing"
      data-test="arch-modal"
      class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center z-50"
    >
      <div class="bg-white rounded-lg max-w-lg w-full">
        <div class="px-4 py-3 border-b border-gray-200">
          <h3 class="text-lg font-medium text-gray-900">
            {{ modalIsCreate ? t('archetypes.modal.createTitle') : t('archetypes.modal.editTitle') }}
          </h3>
        </div>
        <div class="px-4 py-3 space-y-3">
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('archetypes.field.code') }}</label>
            <input
              v-model="editing.code"
              data-test="arch-modal-code"
              class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('archetypes.field.name') }}</label>
            <input
              v-model="editing.name"
              data-test="arch-modal-name"
              class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">{{ t('archetypes.field.description') }}</label>
            <textarea v-model="editing.description" rows="2" class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm text-sm px-3 py-2"></textarea>
          </div>
          <label class="inline-flex items-center text-sm gap-2">
            <input v-model="editing.isActive" type="checkbox" /> {{ t('archetypes.field.isActive') }}
          </label>
        </div>
        <div class="px-4 py-3 border-t border-gray-200 flex justify-end gap-2">
          <button @click="showModal = false" data-test="arch-modal-cancel" class="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">{{ t('common.cancel') }}</button>
          <button @click="onSave" data-test="arch-modal-save" class="px-4 py-2 border border-transparent rounded-md text-sm font-medium text-white bg-blue-600 hover:bg-blue-700">{{ t('common.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
