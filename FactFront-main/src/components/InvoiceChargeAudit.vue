<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { chargeRecordService, type ChargeRecord } from '../services/chargeRecordService'

const { t } = useI18n()

const props = defineProps<{
  invoiceId: string | null
  invoiceNumber?: string
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const records = ref<ChargeRecord[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

watch(() => props.invoiceId, async (id) => {
  if (!id) { records.value = []; return }
  loading.value = true
  error.value = null
  try {
    records.value = await chargeRecordService.byInvoice(id)
  } catch (e: any) {
    error.value = t('invoiceChargeAudit.errorLoading')
    records.value = []
  } finally {
    loading.value = false
  }
}, { immediate: true })

function formatInputs(inputs: Record<string, unknown>): string {
  return Object.entries(inputs)
    .map(([k, v]) => `${k}: ${v}`)
    .join(' · ')
}

function calculatorLabel(calc: string): string {
  return calc.replace('Calculator', '').replace(/([A-Z])/g, ' $1').trim()
}
</script>

<template>
  <div
    v-if="invoiceId"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    @click.self="emit('close')"
  >
    <div class="bg-[rgba(255,253,247,0.92)] rounded-xl shadow-2xl w-full max-w-3xl max-h-[85vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-[rgba(60,50,35,0.12)]">
        <div>
          <h2 class="text-lg font-semibold text-tide-ink">{{ $t('invoiceChargeAudit.title') }}</h2>
          <p v-if="invoiceNumber" class="text-sm text-tide-ink/55">{{ invoiceNumber }}</p>
        </div>
        <button @click="emit('close')" class="text-tide-ink/40 hover:text-tide-ink/70 transition-colors text-2xl leading-none">×</button>
      </div>

      <!-- Body -->
      <div class="flex-1 overflow-y-auto p-6">
        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-2 border-tide-blue border-t-transparent"></div>
        </div>

        <!-- Error -->
        <div v-else-if="error" class="text-center py-8 text-red-500">{{ error }}</div>

        <!-- Empty -->
        <div v-else-if="records.length === 0" class="text-center py-12">
          <p class="text-tide-ink/40 text-sm">{{ $t('invoiceChargeAudit.empty') }}</p>
          <p class="text-gray-300 text-xs mt-1">{{ $t('invoiceChargeAudit.emptyHint') }}</p>
        </div>

        <!-- Records -->
        <div v-else class="space-y-4">
          <div
            v-for="(rec, i) in records"
            :key="rec.id ?? i"
            class="border border-[rgba(60,50,35,0.12)] rounded-lg overflow-hidden"
          >
            <!-- Record header -->
            <div class="flex items-start justify-between px-4 py-3 bg-[rgba(252,247,238,0.55)]">
              <div class="space-y-0.5">
                <div class="flex items-center gap-2">
                  <span class="text-xs font-medium bg-blue-100 text-tide-blue-deep px-2 py-0.5 rounded">
                    {{ calculatorLabel(rec.calculatorUsed) }}
                  </span>
                  <span v-if="rec.contractName" class="text-sm text-tide-ink/70">{{ rec.contractName }}</span>
                </div>
                <p class="text-xs text-tide-ink/40 font-mono">{{ $t('invoiceChargeAudit.item') }}: {{ rec.itemId }}</p>
              </div>
              <div class="text-right">
                <p class="text-lg font-bold text-tide-ink">
                  {{ rec.amount?.toFixed(2) }} {{ rec.currency }}
                </p>
                <p class="text-xs text-tide-ink/40">{{ rec.quantity }} {{ rec.uom }}</p>
              </div>
            </div>

            <!-- Explanation -->
            <div class="px-4 py-3 border-t border-[rgba(42,36,30,0.07)]">
              <p class="text-sm text-tide-ink/80 font-medium">{{ rec.explanation }}</p>
            </div>

            <!-- Inputs -->
            <div v-if="rec.inputs && Object.keys(rec.inputs).length" class="px-4 py-2 bg-[rgba(252,247,238,0.55)] border-t border-[rgba(42,36,30,0.07)]">
              <p class="text-xs text-tide-ink/40 font-mono">{{ formatInputs(rec.inputs) }}</p>
            </div>

            <!-- Footer -->
            <div class="px-4 py-2 border-t border-[rgba(42,36,30,0.07)] flex items-center justify-between">
              <span
                :class="{
                  'bg-yellow-100 text-yellow-700': rec.status === 'PENDING',
                  'bg-green-100 text-green-700': rec.status === 'INVOICED',
                  'bg-[rgba(42,36,30,0.05)] text-tide-ink/55': rec.status === 'CANCELLED',
                }"
                class="text-xs px-2 py-0.5 rounded font-medium"
              >{{ rec.status }}</span>
              <span v-if="rec.calculatedAt" class="text-xs text-tide-ink/40">
                {{ new Date(rec.calculatedAt).toLocaleString() }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
