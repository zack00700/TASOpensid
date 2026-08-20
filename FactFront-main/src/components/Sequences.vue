<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { sequenceService } from '../services/sequenceService'
import SequenceForm from './SequenceForm.vue'
import type { InvoiceSequence } from '../types/sequence'

const { t } = useI18n()

const sequences = ref<InvoiceSequence[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const editTarget = ref<InvoiceSequence | null>(null)
const showForm = ref(false)

async function loadSequences() {
  loading.value = true
  error.value = null
  try {
    sequences.value = await sequenceService.list()
  } catch {
    error.value = t('sequences.errorLoading')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editTarget.value = null
  showForm.value = true
}

function openEdit(seq: InvoiceSequence) {
  editTarget.value = seq
  showForm.value = true
}

async function onSaved() {
  showForm.value = false
  await loadSequences()
}

onMounted(loadSequences)
</script>

<template>
  <div class="p-6 max-w-5xl mx-auto">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-semibold text-tide-ink">{{ t('nav.invoiceSequences') }}</h1>
        <p class="text-sm text-tide-ink/55 mt-1">
          {{ t('sequences.subtitle') }}
        </p>
      </div>
      <button
        @click="openCreate"
        class="px-4 py-2 bg-tide-blue-btn-deep hover:bg-tide-blue-deep text-white text-sm rounded-md font-medium"
      >
        {{ t('sequences.newSequence') }}
      </button>
    </div>

    <!-- Info box -->
    <div class="mb-5 bg-[rgba(90,138,171,0.10)] border border-blue-200 rounded-lg p-4 text-sm text-blue-800">
      <strong>{{ t('sequences.howItWorks.title') }}</strong>
      <i18n-t keypath="sequences.howItWorks.body" tag="span">
        <template #format><span class="font-mono">{{ t('sequences.howItWorks.format') }}</span></template>
        <template #example><span class="font-mono">{{ t('sequences.howItWorks.example') }}</span></template>
      </i18n-t>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="text-center py-12 text-tide-ink/40 text-sm animate-pulse">
      {{ t('sequences.loadingSequences') }}
    </div>

    <!-- Error -->
    <div v-else-if="error" class="p-4 bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg">
      {{ error }}
      <button @click="loadSequences" class="ml-3 underline">{{ t('itemEventHistory.retry') }}</button>
    </div>

    <!-- Table -->
    <div v-else class="border border-[rgba(60,50,35,0.12)] rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-[rgba(252,247,238,0.55)] border-b border-[rgba(60,50,35,0.12)]">
          <tr>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.sequenceId') }}</th>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.prefix') }}</th>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.nextValue') }}</th>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.maxDigits') }}</th>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.nextNumber') }}</th>
            <th class="px-4 py-3 text-left font-medium text-tide-ink/70">{{ t('sequences.column.scope') }}</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-[rgba(42,36,30,0.06)]">
          <tr v-for="seq in sequences" :key="seq.id" class="hover:bg-[rgba(252,247,238,0.55)]">
            <td class="px-4 py-3">
              <span class="font-mono font-medium text-tide-ink">{{ seq.sequenceId }}</span>
              <span
                v-if="seq.isDefault"
                class="ml-2 text-xs px-1.5 py-0.5 bg-[rgba(90,138,171,0.10)] text-tide-blue-deep rounded font-medium"
              >{{ t('sequences.defaultBadge') }}</span>
            </td>
            <td class="px-4 py-3 font-mono text-tide-ink/70">{{ seq.prefix || '—' }}</td>
            <td class="px-4 py-3 text-tide-ink/70 tabular-nums">{{ seq.nextValue }}</td>
            <td class="px-4 py-3 text-tide-ink/70">{{ seq.maximumDigits }}</td>
            <td class="px-4 py-3">
              <span class="font-mono font-semibold text-green-700">{{ seq.previewExample }}</span>
            </td>
            <td class="px-4 py-3 text-tide-ink/55 text-xs">
              {{ seq.invoiceTypeId || t('sequences.scope.global') }}
            </td>
            <td class="px-4 py-3 text-right">
              <button
                @click="openEdit(seq)"
                class="text-sm text-tide-blue-deep hover:text-blue-800 font-medium"
              >
                {{ t('common.edit') }}
              </button>
            </td>
          </tr>
          <tr v-if="sequences.length === 0">
            <td colspan="7" class="px-4 py-10 text-center text-tide-ink/40">
              {{ t('sequences.empty') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Form modal -->
    <SequenceForm
      v-if="showForm"
      :sequence="editTarget"
      @saved="onSaved"
      @cancel="showForm = false"
    />
  </div>
</template>
