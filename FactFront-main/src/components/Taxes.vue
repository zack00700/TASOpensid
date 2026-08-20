<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Pencil, Plus, Trash2, X } from 'lucide-vue-next';
import { useTax } from '../composables/use.tax';
import { useAuthStore } from '../stores/authStore';
import { TAX_TYPES, type Tax } from '../types/tax';

const { t } = useI18n();
const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasRole('ROLE_ADMIN'));

const { taxes, loading, errors, getAll, create, update, remove } = useTax();

const search = ref('');
const includeInactive = ref(true);

const showModal = ref(false);
const editing = ref<Tax | null>(null);
const isCreate = ref(false);
const saving = ref(false);

const showDeleteConfirm = ref(false);
const toDelete = ref<Tax | null>(null);

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase();
  return taxes.value.filter((tax) => {
    if (!includeInactive.value && tax.isActive === false) return false;
    if (!q) return true;
    return (
      tax.code.toLowerCase().includes(q) ||
      tax.name.toLowerCase().includes(q)
    );
  });
});

function emptyTax(): Tax {
  return {
    name: '',
    code: '',
    type: 'PERCENTAGE',
    rate: 0,
    validFrom: null,
    validTo: null,
    isActive: true,
  };
}

function onAdd() {
  isCreate.value = true;
  editing.value = emptyTax();
  showModal.value = true;
}

function onEdit(tax: Tax) {
  isCreate.value = false;
  editing.value = { ...tax };
  showModal.value = true;
}

function closeModal() {
  showModal.value = false;
  editing.value = null;
  saving.value = false;
}

async function onSave() {
  if (!editing.value) return;
  if (!editing.value.code.trim() || !editing.value.name.trim()) return;
  saving.value = true;
  const ok = isCreate.value
    ? await create(editing.value)
    : await update(editing.value.id!, editing.value);
  if (ok) {
    closeModal();
    await getAll();
  } else {
    saving.value = false;
  }
}

function onAskDelete(tax: Tax) {
  toDelete.value = tax;
  showDeleteConfirm.value = true;
}

async function onConfirmDelete() {
  if (!toDelete.value?.id) return;
  await remove(toDelete.value.id);
  showDeleteConfirm.value = false;
  toDelete.value = null;
  await getAll();
}

function formatRate(tax: Tax) {
  if (tax.type === 'PERCENTAGE') return `${tax.rate} %`;
  return tax.rate.toString();
}

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString();
  } catch {
    return iso;
  }
}

// `<input type="date">` returns YYYY-MM-DD; backend expects an ISO Instant.
// Promote to start-of-day UTC on write, and slice incoming ISO down on read.
function toDateInput(iso: string | null | undefined): string {
  if (!iso) return '';
  return iso.slice(0, 10);
}

function fromDateInput(date: string): string | null {
  if (!date) return null;
  return new Date(`${date}T00:00:00Z`).toISOString();
}

onMounted(async () => {
  await getAll();
});
</script>

<template>
  <div class="p-6 space-y-4">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-xl font-semibold text-tide-ink">{{ t('taxes.title') }}</h1>
        <p class="text-sm text-tide-ink/55">{{ t('taxes.subtitle') }}</p>
      </div>
      <button
        v-if="isAdmin"
        data-test="taxes-add"
        class="inline-flex items-center gap-2 px-3 py-2 bg-tide-blue-btn-deep text-white text-sm rounded-md hover:bg-tide-blue-deep"
        @click="onAdd"
      >
        <Plus class="h-4 w-4" />
        {{ t('taxes.action.new') }}
      </button>
    </div>

    <div class="bg-[rgba(255,253,247,0.92)] shadow rounded-lg">
      <div class="p-4 flex flex-wrap gap-3 items-center border-b border-[rgba(60,50,35,0.12)]">
        <input
          v-model="search"
          type="text"
          data-test="taxes-search"
          :placeholder="t('taxes.placeholder.search')"
          class="block w-64 rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue text-sm"
        />
        <label class="inline-flex items-center gap-2 text-sm text-tide-ink/80">
          <input
            v-model="includeInactive"
            type="checkbox"
            data-test="taxes-include-inactive"
            class="rounded border-[rgba(60,50,35,0.16)] text-tide-blue-deep focus:ring-tide-blue"
          />
          {{ t('taxes.filter.includeInactive') }}
        </label>
      </div>

      <p v-if="errors.network" data-test="taxes-error-network" class="px-4 py-2 text-sm text-red-600">
        {{ errors.network }}
      </p>

      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-[rgba(42,36,30,0.08)]">
          <thead class="bg-[rgba(252,247,238,0.55)]">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.code') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.name') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.type') }}</th>
              <th class="px-4 py-3 text-right text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.rate') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.validFrom') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.validTo') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.active') }}</th>
              <th class="px-4 py-3 text-right text-xs font-medium text-tide-ink/55 uppercase">{{ t('taxes.col.actions') }}</th>
            </tr>
          </thead>
          <tbody class="bg-[rgba(255,253,247,0.92)] divide-y divide-[rgba(42,36,30,0.08)]">
            <tr v-if="!loading && filtered.length === 0">
              <td colspan="8" class="px-4 py-8 text-center text-sm text-tide-ink/55 italic" data-test="taxes-empty">
                {{ t('taxes.empty') }}
              </td>
            </tr>
            <tr v-for="tax in filtered" :key="tax.id" :data-test="`tax-row-${tax.id}`" class="hover:bg-[rgba(252,247,238,0.55)]">
              <td class="px-4 py-3 text-sm font-medium text-tide-ink">{{ tax.code }}</td>
              <td class="px-4 py-3 text-sm text-tide-ink/80">{{ tax.name }}</td>
              <td class="px-4 py-3 text-sm text-tide-ink/55">
                {{ tax.type === 'PERCENTAGE' ? t('taxes.type.percentage') : t('taxes.type.fixed') }}
              </td>
              <td class="px-4 py-3 text-sm text-tide-ink text-right whitespace-nowrap">{{ formatRate(tax) }}</td>
              <td class="px-4 py-3 text-sm text-tide-ink/55">{{ formatDate(tax.validFrom) }}</td>
              <td class="px-4 py-3 text-sm text-tide-ink/55">{{ formatDate(tax.validTo) }}</td>
              <td class="px-4 py-3 text-sm">
                <span
                  :class="[
                    'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
                    tax.isActive
                      ? 'bg-green-100 text-green-800'
                      : 'bg-[rgba(42,36,30,0.05)] text-tide-ink/70',
                  ]"
                >{{ tax.isActive ? t('taxes.status.active') : t('taxes.status.inactive') }}</span>
              </td>
              <td class="px-4 py-3 text-sm text-right">
                <div class="inline-flex gap-2 justify-end">
                  <button
                    v-if="isAdmin"
                    :data-test="`tax-edit-${tax.id}`"
                    class="text-tide-blue-deep hover:text-tide-blue-deep"
                    :aria-label="t('common.edit')"
                    @click="onEdit(tax)"
                  >
                    <Pencil class="h-4 w-4" />
                  </button>
                  <button
                    v-if="isAdmin"
                    :data-test="`tax-delete-${tax.id}`"
                    class="text-red-600 hover:text-red-900"
                    :aria-label="t('common.delete')"
                    @click="onAskDelete(tax)"
                  >
                    <Trash2 class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create / edit modal -->
    <Teleport to="body">
      <div
        v-if="showModal && editing"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-lg shadow-xl p-6 w-full max-w-lg space-y-4">
          <div class="flex justify-between items-center">
            <h2 class="text-lg font-semibold text-tide-ink">
              {{ isCreate ? t('taxes.modal.titleCreate') : t('taxes.modal.titleEdit') }}
            </h2>
            <button type="button" class="text-tide-ink/55" :aria-label="t('common.close')" @click="closeModal">
              <X class="h-5 w-5" />
            </button>
          </div>

          <p v-if="errors.create || errors.update" class="text-sm text-red-600" data-test="tax-modal-error">
            {{ errors.create || errors.update }}
          </p>

          <form class="space-y-3" @submit.prevent="onSave">
            <div>
              <label class="block text-sm font-medium text-tide-ink/80">{{ t('taxes.field.code') }} <span class="text-red-500">*</span></label>
              <input
                v-model="editing.code"
                data-test="tax-form-code"
                type="text"
                required
                class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-tide-ink/80">{{ t('taxes.field.name') }} <span class="text-red-500">*</span></label>
              <input
                v-model="editing.name"
                data-test="tax-form-name"
                type="text"
                required
                class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
              />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-sm font-medium text-tide-ink/80">{{ t('taxes.field.type') }} <span class="text-red-500">*</span></label>
                <select
                  v-model="editing.type"
                  data-test="tax-form-type"
                  class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
                >
                  <option v-for="opt in TAX_TYPES" :key="opt" :value="opt">
                    {{ opt === 'PERCENTAGE' ? t('taxes.type.percentage') : t('taxes.type.fixed') }}
                  </option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-tide-ink/80">
                  {{ editing.type === 'PERCENTAGE' ? t('taxes.field.ratePercent') : t('taxes.field.rateFixed') }}
                  <span class="text-red-500">*</span>
                </label>
                <input
                  v-model.number="editing.rate"
                  data-test="tax-form-rate"
                  type="number"
                  step="0.01"
                  min="0"
                  required
                  class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
                />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-sm font-medium text-tide-ink/80">{{ t('taxes.field.validFrom') }}</label>
                <input
                  :value="toDateInput(editing.validFrom)"
                  data-test="tax-form-valid-from"
                  type="date"
                  class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
                  @input="(e: Event) => editing && (editing.validFrom = fromDateInput((e.target as HTMLInputElement).value))"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-tide-ink/80">{{ t('taxes.field.validTo') }}</label>
                <input
                  :value="toDateInput(editing.validTo)"
                  data-test="tax-form-valid-to"
                  type="date"
                  class="mt-1 block w-full rounded-md border-[rgba(60,50,35,0.16)] shadow-sm focus:border-tide-blue focus:ring-tide-blue"
                  @input="(e: Event) => editing && (editing.validTo = fromDateInput((e.target as HTMLInputElement).value))"
                />
              </div>
            </div>
            <div>
              <label class="inline-flex items-center gap-2 text-sm text-tide-ink/80">
                <input
                  v-model="editing.isActive"
                  data-test="tax-form-active"
                  type="checkbox"
                  class="rounded border-[rgba(60,50,35,0.16)] text-tide-blue-deep focus:ring-tide-blue"
                />
                {{ t('taxes.field.active') }}
              </label>
            </div>

            <div class="flex justify-end gap-2 pt-2">
              <button type="button" class="px-3 py-2 text-sm text-tide-ink/80" @click="closeModal">
                {{ t('common.cancel') }}
              </button>
              <button
                type="submit"
                data-test="tax-form-save"
                :disabled="saving || !editing.code.trim() || !editing.name.trim()"
                class="px-3 py-2 bg-tide-blue-btn-deep text-white text-sm rounded hover:bg-tide-blue-deep disabled:opacity-50"
              >
                {{ t('common.save') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Delete confirmation -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm && toDelete"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      >
        <div class="bg-[rgba(255,253,247,0.92)] rounded-lg shadow-xl p-6 w-full max-w-md space-y-4">
          <h2 class="text-lg font-semibold text-tide-ink">{{ t('taxes.delete.title') }}</h2>
          <p class="text-sm text-tide-ink/80">
            {{ t('taxes.delete.confirm', { code: toDelete.code, name: toDelete.name }) }}
          </p>
          <div class="flex justify-end gap-2">
            <button class="px-3 py-2 text-sm text-tide-ink/80" @click="showDeleteConfirm = false">
              {{ t('common.cancel') }}
            </button>
            <button
              data-test="tax-delete-confirm"
              class="px-3 py-2 bg-red-600 text-white text-sm rounded hover:bg-red-700"
              @click="onConfirmDelete"
            >
              {{ t('common.delete') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
