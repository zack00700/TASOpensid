<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { fetchInvoiceHtml } from '../services/invoiceService';

const { t } = useI18n();

interface Props {
  invoiceId: string;     // UUID used to fetch from backend
  displayId?: string;    // Human-readable label shown in header (finalNumber / draftNumber)
  previewUrl?: string;
  status?: string;
  deleting?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  invoiceId: '',
  displayId: '',
  deleting: false,
});
const emit = defineEmits(['close', 'finalize', 'delete']);

const frame = ref<HTMLIFrameElement | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const htmlContent = ref<string>('');

async function loadHtml() {
  if (!props.invoiceId) return;
  loading.value = true;
  error.value = null;
  try {
    htmlContent.value = await fetchInvoiceHtml(props.invoiceId);
  } catch (e: any) {
    error.value = t('invoicePreview.errorLoading');
  } finally {
    loading.value = false;
  }
}

watch(() => props.invoiceId, loadHtml);
onMounted(loadHtml);

function print() {
  frame.value?.contentWindow?.print();
}

function openInNewTab() {
  if (!htmlContent.value) return;
  const blob = new Blob([htmlContent.value], { type: 'text/html; charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const win = window.open(url, '_blank');
  // Revoke the object URL once the new tab has loaded
  if (win) {
    win.addEventListener('load', () => URL.revokeObjectURL(url), { once: true });
  } else {
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50"
      role="dialog"
      aria-modal="true"
    >
      <div class="bg-white w-full h-full md:max-w-[1024px] md:h-[90vh] md:rounded-lg shadow-lg flex flex-col">
        <header class="flex items-center justify-between p-4 border-b">
          <div class="flex items-center gap-2">
            <h2 class="text-lg font-semibold">{{ $t('invoicePreview.invoice') }} {{ displayId || invoiceId }}</h2>
            <span v-if="status" class="px-2 py-1 text-xs rounded-full bg-yellow-100 text-yellow-800">{{ status }}</span>
          </div>
          <div class="flex items-center gap-2">
            <button
              v-if="status === 'DRAFT'"
              class="px-3 py-1 border rounded text-red-600"
              @click="emit('delete')"
              :aria-label="$t('invoicePreview.label.deleteInvoice')"
              :title="$t('invoicePreview.label.deleteDraft')"
              :disabled="deleting"
              :class="deleting ? 'opacity-50 cursor-not-allowed' : ''"
            >
              {{ $t('invoicePreview.button.delete') }}
            </button>
            <button
              v-if="status === 'DRAFT'"
              class="px-3 py-1 border rounded"
              @click="emit('finalize')"
              :aria-label="$t('invoicePreview.label.finalizeInvoice')"
            >
              {{ $t('invoicePreview.button.finalize') }}
            </button>
            <button
              class="px-3 py-1 border rounded"
              @click="print"
              :disabled="loading || !!error"
              :aria-label="$t('invoicePreview.label.printInvoice')"
            >
              {{ $t('invoicePreview.button.print') }}
            </button>
            <button
              class="px-3 py-1 border rounded"
              @click="openInNewTab"
              :disabled="loading || !!error"
              :aria-label="$t('invoicePreview.label.openInNewTab')"
            >
              {{ $t('invoicePreview.button.open') }}
            </button>
            <button
              class="px-3 py-1 border rounded"
              @click="emit('close')"
              :aria-label="$t('invoicePreview.label.closePreview')"
            >
              {{ $t('common.close') }}
            </button>
          </div>
        </header>
        <section class="flex-1 bg-gray-50 relative">
          <div
            v-if="loading"
            class="absolute inset-0 flex items-center justify-center bg-gray-100 animate-pulse"
          >
            <div class="w-16 h-16 bg-gray-200 rounded" />
          </div>
          <div
            v-else-if="error"
            class="absolute inset-0 flex items-center justify-center text-red-600 text-sm"
          >
            {{ error }}
          </div>
          <iframe
            v-else-if="htmlContent"
            :srcdoc="htmlContent"
            ref="frame"
            class="w-full h-full border-0"
            sandbox="allow-same-origin allow-modals"
          />
        </section>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
</style>
