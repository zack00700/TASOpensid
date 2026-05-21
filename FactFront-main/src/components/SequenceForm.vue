<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { sequenceService } from '../services/sequenceService'
import type { InvoiceSequence, InvoiceSequenceForm } from '../types/sequence'

const { t } = useI18n()

const props = defineProps<{ sequence: InvoiceSequence | null }>()
const emit = defineEmits(['saved', 'cancel'])

const isEdit = computed(() => props.sequence !== null)

const form = ref<InvoiceSequenceForm>({
  sequenceId: '',
  prefix: '',
  nextValue: 1,
  maximumDigits: 5,
  invoiceTypeId: null,
})

const errors = ref<Partial<Record<keyof InvoiceSequenceForm, string>>>({})
const saving = ref(false)
const saveError = ref<string | null>(null)

const localPreview = computed(() => {
  const p = form.value.prefix || ''
  const n = Math.max(1, form.value.nextValue || 1)
  const d = Math.max(1, form.value.maximumDigits || 5)
  return p + String(n).padStart(d, '0')
})

const rangeInfo = computed(() => {
  const p = form.value.prefix || ''
  const d = Math.max(1, form.value.maximumDigits || 5)
  const min = p + '1'.padStart(d, '0')
  const max = p + String(Math.pow(10, d) - 1)
  return `${min} → ${max}`
})

watch(() => props.sequence, (seq) => {
  if (seq) {
    form.value = {
      sequenceId: seq.sequenceId,
      prefix: seq.prefix ?? '',
      nextValue: seq.nextValue,
      maximumDigits: seq.maximumDigits,
      invoiceTypeId: seq.invoiceTypeId,
    }
  } else {
    form.value = { sequenceId: '', prefix: '', nextValue: 1, maximumDigits: 5, invoiceTypeId: null }
  }
  errors.value = {}
  saveError.value = null
}, { immediate: true })

function validate(): boolean {
  errors.value = {}
  if (!form.value.sequenceId.trim()) {
    errors.value.sequenceId = t('sequenceForm.validation.required')
  }
  if (form.value.nextValue < 1) {
    errors.value.nextValue = t('sequenceForm.validation.mustBeAtLeastOne')
  }
  if (form.value.maximumDigits < 1 || form.value.maximumDigits > 10) {
    errors.value.maximumDigits = t('sequenceForm.validation.between1And10')
  }
  const maxAllowed = Math.pow(10, form.value.maximumDigits) - 1
  if (form.value.nextValue > maxAllowed) {
    errors.value.nextValue = t('sequenceForm.validation.maxWithDigits', { digits: form.value.maximumDigits, max: maxAllowed })
  }
  return Object.keys(errors.value).length === 0
}

async function submit() {
  if (!validate()) return
  saving.value = true
  saveError.value = null
  try {
    if (isEdit.value && props.sequence) {
      await sequenceService.update(props.sequence.sequenceId, form.value)
    } else {
      await sequenceService.create(form.value)
    }
    emit('saved')
  } catch (e: any) {
    saveError.value = e?.response?.data?.message || e?.message || t('sequenceForm.error.savingSequence')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-40">
      <div class="bg-white rounded-lg shadow-xl w-full max-w-lg p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-5">
          {{ isEdit ? t('sequenceForm.header.editTitle') : t('sequenceForm.header.newTitle') }}
        </h2>

        <div class="space-y-4">
          <!-- Sequence ID -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('sequences.column.sequenceId') }}</label>
            <input
              v-model="form.sequenceId"
              :disabled="isEdit"
              class="w-full border rounded px-3 py-2 text-sm font-mono uppercase disabled:bg-gray-100 disabled:text-gray-500"
              :class="errors.sequenceId ? 'border-red-400' : 'border-gray-300'"
              :placeholder="t('sequenceForm.placeholder.invoiceFinal')"
            />
            <p v-if="errors.sequenceId" class="text-red-500 text-xs mt-1">{{ errors.sequenceId }}</p>
            <p v-if="isEdit" class="text-gray-400 text-xs mt-1">{{ t('sequenceForm.sequenceIdCannotBeChanged') }}</p>
          </div>

          <!-- Prefix + MaxDigits -->
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('sequences.column.prefix') }}</label>
              <input
                v-model="form.prefix"
                class="w-full border border-gray-300 rounded px-3 py-2 text-sm font-mono"
                :placeholder="t('sequenceForm.placeholder.inv')"
              />
              <p class="text-gray-400 text-xs mt-1">{{ t('sequenceForm.emptyNoPrefix') }}</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('sequenceForm.maximumDigits') }}</label>
              <input
                v-model.number="form.maximumDigits"
                type="number" min="1" max="10"
                class="w-full border rounded px-3 py-2 text-sm"
                :class="errors.maximumDigits ? 'border-red-400' : 'border-gray-300'"
              />
              <p v-if="errors.maximumDigits" class="text-red-500 text-xs mt-1">{{ errors.maximumDigits }}</p>
            </div>
          </div>

          <!-- Next Value -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('sequenceForm.nextSequenceValue') }}</label>
            <input
              v-model.number="form.nextValue"
              type="number" min="1"
              class="w-full border rounded px-3 py-2 text-sm"
              :class="errors.nextValue ? 'border-red-400' : 'border-gray-300'"
            />
            <p v-if="errors.nextValue" class="text-red-500 text-xs mt-1">{{ errors.nextValue }}</p>
          </div>

          <!-- Live Preview -->
          <div class="bg-gray-50 border border-gray-200 rounded p-3">
            <p class="text-xs text-gray-500 mb-1">{{ t('sequenceForm.nextNumberPreview') }}</p>
            <p class="font-mono text-lg font-semibold text-green-700">{{ localPreview }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ t('sequenceForm.range', { range: rangeInfo }) }}</p>
          </div>

          <!-- Error -->
          <div v-if="saveError" class="text-red-600 text-sm bg-red-50 border border-red-200 rounded p-3">
            {{ saveError }}
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6">
          <button
            @click="emit('cancel')"
            class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            @click="submit"
            :disabled="saving"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm rounded font-medium disabled:opacity-60"
          >
            {{ saving ? t('sequenceForm.button.saving') : (isEdit ? t('sequenceForm.button.saveChanges') : t('sequenceForm.button.createSequence')) }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
