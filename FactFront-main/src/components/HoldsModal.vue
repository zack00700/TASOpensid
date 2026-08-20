<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-lg shadow-xl p-6 w-full max-w-2xl space-y-4 max-h-[90vh] overflow-y-auto">
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ t('holdsModal.title', { vesselName: visit?.vesselName ?? '' }) }}
        </h2>
        <button type="button" class="text-gray-500" @click="emit('close')" :aria-label="t('common.close')">
          <X class="h-5 w-5" />
        </button>
      </div>

      <p v-if="error" data-test="holds-error" class="text-sm text-red-600">{{ error }}</p>

      <!-- Holds list -->
      <div>
        <div class="text-sm font-medium text-gray-700 mb-2">{{ t('holdsModal.list.title') }}</div>
        <div v-if="holds.length === 0" data-test="holds-empty" class="text-sm text-gray-500">
          {{ t('holdsModal.list.empty') }}
        </div>
        <ul v-else class="divide-y divide-gray-200 border border-gray-200 rounded-md max-h-72 overflow-y-auto">
          <li
            v-for="h in holds"
            :key="h.id"
            :data-test="`hold-row-${h.id}`"
            class="px-3 py-2 text-sm"
          >
            <div class="flex justify-between items-start gap-3">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span
                    :class="[
                      'inline-block px-2 py-0.5 text-xs font-medium rounded-full',
                      h.active ? 'bg-amber-100 text-amber-800' : 'bg-gray-100 text-gray-600',
                    ]"
                  >{{ h.type }}</span>
                  <span v-if="h.active" class="text-xs font-medium text-amber-700">{{ t('holdsModal.status.active') }}</span>
                  <span v-else class="text-xs text-gray-500">{{ t('holdsModal.status.released') }}</span>
                </div>
                <div class="mt-1 text-gray-700">{{ h.reason }}</div>
                <div class="mt-1 text-xs text-gray-500">
                  {{ t('holdsModal.opened', { who: h.openedBy, when: formatDate(h.openedAt) }) }}
                </div>
                <div v-if="!h.active" class="text-xs text-gray-500">
                  {{ t('holdsModal.released', { who: h.releasedBy ?? '?', when: formatDate(h.releasedAt) }) }}
                  <span v-if="h.releaseNotes"> — “{{ h.releaseNotes }}”</span>
                </div>
              </div>
              <div v-if="h.active" class="shrink-0">
                <button
                  type="button"
                  :data-test="`hold-release-${h.id}`"
                  :disabled="releasingId === h.id"
                  class="text-xs px-2 py-1 rounded border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                  @click="openReleaseFor(h)"
                >
                  {{ t('holdsModal.action.release') }}
                </button>
              </div>
            </div>

            <!-- Inline release form -->
            <div v-if="releaseTargetId === h.id" class="mt-2 space-y-2">
              <textarea
                v-model="releaseNotes"
                rows="2"
                maxlength="500"
                :placeholder="t('holdsModal.release.notesPlaceholder')"
                :data-test="`hold-release-notes-${h.id}`"
                class="block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm text-sm"
              ></textarea>
              <div class="flex justify-end gap-2">
                <button
                  type="button"
                  class="px-3 py-1 text-xs text-gray-700"
                  @click="cancelRelease"
                >{{ t('common.cancel') }}</button>
                <button
                  type="button"
                  :data-test="`hold-release-confirm-${h.id}`"
                  :disabled="releasingId === h.id"
                  class="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
                  @click="confirmRelease(h)"
                >{{ t('holdsModal.release.confirm') }}</button>
              </div>
            </div>
          </li>
        </ul>
      </div>

      <!-- Add hold form -->
      <div class="border-t border-gray-200 pt-4">
        <div class="text-sm font-medium text-gray-700 mb-2">{{ t('holdsModal.add.title') }}</div>
        <div class="space-y-3">
          <div>
            <label class="block text-xs font-medium text-gray-600">{{ t('holdsModal.add.type') }} <span class="text-red-500">*</span></label>
            <select
              v-model="newType"
              data-test="hold-add-type"
              class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm text-sm"
            >
              <option v-for="t_ in HOLD_TYPES" :key="t_" :value="t_">{{ t_ }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-600">{{ t('holdsModal.add.reason') }} <span class="text-red-500">*</span></label>
            <textarea
              v-model="newReason"
              rows="2"
              maxlength="500"
              data-test="hold-add-reason"
              :placeholder="t('holdsModal.add.reasonPlaceholder')"
              class="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm text-sm"
            ></textarea>
          </div>
          <div class="flex justify-end">
            <button
              type="button"
              data-test="hold-add-submit"
              :disabled="!canAdd || adding"
              class="px-3 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
              @click="submitAdd"
            >{{ t('holdsModal.add.submit') }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { X } from 'lucide-vue-next';
import { useHold } from '../composables/use.hold';
import { HOLD_TYPES, type Hold, type HoldType } from '../types/hold';
import type { VesselVisit } from '../types/vessel-visit';

const props = defineProps<{
  open: boolean;
  visit: VesselVisit | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'changed'): void;
}>();

const { t } = useI18n();
const { holds, listForVisit, createHold, releaseHold } = useHold();

const newType = ref<HoldType>('Customs');
const newReason = ref('');
const adding = ref(false);
const releaseTargetId = ref<string | null>(null);
const releaseNotes = ref('');
const releasingId = ref<string | null>(null);
const error = ref('');

const canAdd = computed(() => !!props.visit?.id && newReason.value.trim().length > 0);

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

async function refresh() {
  if (!props.open || !props.visit?.id) return;
  error.value = '';
  try {
    await listForVisit(props.visit.id);
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? e?.message ?? String(e);
  }
}

async function submitAdd() {
  if (!canAdd.value || !props.visit?.id) return;
  adding.value = true;
  error.value = '';
  try {
    await createHold(props.visit.id, { type: newType.value, reason: newReason.value.trim() });
    newReason.value = '';
    await refresh();
    emit('changed');
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? e?.message ?? String(e);
  } finally {
    adding.value = false;
  }
}

function openReleaseFor(h: Hold) {
  releaseTargetId.value = h.id;
  releaseNotes.value = '';
}

function cancelRelease() {
  releaseTargetId.value = null;
  releaseNotes.value = '';
}

async function confirmRelease(h: Hold) {
  releasingId.value = h.id;
  error.value = '';
  try {
    await releaseHold(h.id, releaseNotes.value);
    releaseTargetId.value = null;
    releaseNotes.value = '';
    await refresh();
    emit('changed');
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? e?.message ?? String(e);
  } finally {
    releasingId.value = null;
  }
}

onMounted(refresh);
watch(
  () => [props.open, props.visit?.id],
  () => {
    if (props.open) refresh();
  },
);
</script>
