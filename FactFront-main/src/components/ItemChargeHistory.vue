<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { chargeRecordService, type ChargeRecord } from '../services/chargeRecordService'

const { t } = useI18n()

const props = defineProps<{
  itemId: string | null
  itemNumber?: string
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const records = ref<ChargeRecord[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

watch(() => props.itemId, async (id) => {
  if (!id) { records.value = []; return }
  loading.value = true
  error.value = null
  try {
    records.value = await chargeRecordService.byItem(id)
  } catch (e: any) {
    error.value = t('itemChargeHistory.error.loadFailed')
    records.value = []
  } finally {
    loading.value = false
  }
}, { immediate: true })

const total = () => records.value.reduce((sum, r) => sum + (r.amount ?? 0), 0)
const currency = () => records.value[0]?.currency ?? ''

function calculatorLabel(calc: string): string {
  return calc.replace('Calculator', '').replace(/([A-Z])/g, ' $1').trim()
}
</script>

<template>
  <div
    v-if="itemId"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    @click.self="emit('close')"
  >
    <div class="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[85vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200">
        <div>
          <h2 class="text-lg font-semibold text-gray-900">{{ t('itemChargeHistory.title') }}</h2>
          <p v-if="itemNumber" class="text-sm text-gray-500">{{ itemNumber }}</p>
        </div>
        <button @click="emit('close')" class="text-gray-400 hover:text-gray-600 text-2xl leading-none">×</button>
      </div>

      <!-- Summary bar -->
      <div v-if="records.length > 0" class="px-6 py-3 bg-gray-50 border-b border-gray-200 flex items-center gap-6">
        <div>
          <p class="text-xs text-gray-500">{{ t('itemChargeHistory.totalCharged') }}</p>
          <p class="text-xl font-bold text-gray-900">{{ total().toFixed(2) }} {{ currency() }}</p>
        </div>
        <div>
          <p class="text-xs text-gray-500">{{ t('itemChargeHistory.records') }}</p>
          <p class="text-xl font-bold text-gray-900">{{ records.length }}</p>
        </div>
      </div>

      <!-- Body -->
      <div class="flex-1 overflow-y-auto p-6">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-2 border-blue-500 border-t-transparent"></div>
        </div>

        <div v-else-if="error" class="text-center py-8 text-red-500">{{ error }}</div>

        <div v-else-if="records.length === 0" class="text-center py-12">
          <p class="text-gray-400 text-sm">{{ t('itemChargeHistory.empty') }}</p>
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="(rec, i) in records"
            :key="rec.id ?? i"
            class="border border-gray-200 rounded-lg p-4"
          >
            <div class="flex items-start justify-between mb-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-xs font-medium bg-blue-100 text-blue-700 px-2 py-0.5 rounded">
                  {{ calculatorLabel(rec.calculatorUsed) }}
                </span>
                <span v-if="rec.contractName" class="text-sm text-gray-600">{{ rec.contractName }}</span>
                <span
                  :class="{
                    'bg-yellow-100 text-yellow-700': rec.status === 'PENDING',
                    'bg-green-100 text-green-700': rec.status === 'INVOICED',
                    'bg-gray-100 text-gray-500': rec.status === 'CANCELLED',
                  }"
                  class="text-xs px-2 py-0.5 rounded"
                >{{ rec.status }}</span>
              </div>
              <span class="text-base font-semibold text-gray-900 whitespace-nowrap">
                {{ rec.amount?.toFixed(2) }} {{ rec.currency }}
              </span>
            </div>

            <p class="text-sm text-gray-700">{{ rec.explanation }}</p>

            <div class="flex items-center justify-between mt-2">
              <p v-if="rec.invoiceId" class="text-xs text-gray-400">
                {{ t('invoiceDataSelector.label.invoice') }} <span class="font-mono">{{ rec.invoiceId }}</span>
              </p>
              <p v-if="rec.calculatedAt" class="text-xs text-gray-400 ml-auto">
                {{ new Date(rec.calculatedAt).toLocaleDateString() }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
