<!-- InvoiceDataSelector.vue - Composant pour sélectionner des données d'invoice -->
<template>
    <div class="bg-white border rounded-lg shadow-sm">
      <div class="p-3 border-b">
        <h3 class="text-sm font-medium text-gray-900">{{ $t('invoiceDataSelector.title') }}</h3>
        <p class="text-xs text-gray-500 mt-1">
          {{ $t('invoiceDataSelector.subtitle') }}
        </p>
      </div>
      
      <div class="p-3 space-y-3">
        <!-- Mode de prévisualisation -->
        <div>
          <label class="block text-xs font-medium text-gray-700 mb-1">
            {{ $t('invoiceDataSelector.label.previewMode') }}
          </label>
          <select 
            v-model="previewMode" 
            @change="updatePreviewData"
            class="w-full px-2 py-1 text-xs border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
          >
            <option value="sample">{{ $t('invoiceDataSelector.option.sampleData') }}</option>
            <option value="recent">{{ $t('invoiceDataSelector.option.recentInvoice') }}</option>
            <option value="specific">{{ $t('invoiceDataSelector.option.specificInvoice') }}</option>
          </select>
        </div>
  
        <!-- Sélection d'invoice spécifique -->
        <div v-if="previewMode === 'specific'">
          <label class="block text-xs font-medium text-gray-700 mb-1">
            {{ $t('invoiceDataSelector.label.invoiceNumber') }}
          </label>
          <div class="flex gap-1">
            <input
              v-model="specificInvoiceId"
              type="text"
              :placeholder="$t('invoiceDataSelector.placeholder.invoiceId')"
              class="flex-1 px-2 py-1 text-xs border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
            <button
              @click="loadSpecificInvoice"
              :disabled="!specificInvoiceId || loading"
              class="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {{ $t('invoiceDataSelector.button.load') }}
            </button>
          </div>
        </div>
  
        <!-- Affichage des données sélectionnées -->
        <div v-if="currentData">
          <div class="text-xs text-gray-600 space-y-1">
            <div class="flex justify-between">
              <span>{{ $t('invoiceDataSelector.label.invoice') }}</span>
              <span class="font-medium">{{ currentData.invoice?.number || 'N/A' }}</span>
            </div>
            <div class="flex justify-between">
              <span>{{ $t('invoiceDataSelector.label.customer') }}</span>
              <span class="font-medium">{{ currentData.invoice?.customer?.name || 'N/A' }}</span>
            </div>
            <div class="flex justify-between">
              <span>{{ $t('invoiceDataSelector.label.amount') }}</span>
              <span class="font-medium">{{ formatCurrency(currentData.invoice?.totals?.grand) }}</span>
            </div>
            <div class="flex justify-between">
              <span>{{ $t('invoiceDataSelector.label.items') }}</span>
              <span class="font-medium">{{ currentData.invoice?.items?.length || 0 }}</span>
            </div>
          </div>
        </div>
  
        <!-- Actions -->
        <div class="flex gap-1 pt-2">
          <button
            @click="refreshData"
            :disabled="loading"
            class="flex-1 px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded hover:bg-gray-200 disabled:opacity-50"
          >
            {{ loading ? $t('common.loading') : $t('invoiceDataSelector.button.refresh') }}
          </button>
          <button
            @click="applyDataToTemplate"
            :disabled="!currentData || !studio"
            class="flex-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {{ $t('invoiceDataSelector.button.applyData') }}
          </button>
        </div>
  
        <!-- Erreur -->
        <div v-if="error" class="text-xs text-red-600 bg-red-50 p-2 rounded">
          {{ error }}
        </div>
      </div>
    </div>
  </template>
  
  <script setup lang="ts">
  import { ref, watch, onMounted } from 'vue';
  import { useI18n } from 'vue-i18n';
  import invoiceService from '../services/invoiceService';

  const { t } = useI18n();
  
  interface Props {
    studio?: any;
  }
  
  const props = defineProps<Props>();
  
  const previewMode = ref<'sample' | 'recent' | 'specific'>('sample');
  const specificInvoiceId = ref('');
  const currentData = ref<any>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  
  function formatCurrency(amount: unknown) {
    const num = typeof amount === 'number' ? amount : parseFloat(String(amount));
    return (Number.isFinite(num) ? num : 0).toLocaleString(undefined, {
      style: 'currency',
      currency: 'EUR',
    });
  }
  
  function getSampleData() {
    return {
      invoice: {
        number: 'INV-2025-001',
        issueDate: new Date().toISOString(),
        dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'DRAFT',
        customer: {
          name: 'Acme Corporation',
          address: '123 Business Street',
          city: 'Commerce City',
          postalCode: '75001',
          country: 'France',
          email: 'billing@acme.corp',
          phone: '+33 1 23 45 67 89'
        },
        facility: 'Main Warehouse',
        items: [
          {
            position: 1,
            description: 'Professional Services',
            qty: 10,
            price: 150.00,
            amount: 1500.00
          },
          {
            position: 2,
            description: 'Consultation Hours',
            qty: 5,
            price: 200.00,
            amount: 1000.00
          },
          {
            position: 3,
            description: 'Software License',
            qty: 1,
            price: 500.00,
            amount: 500.00
          }
        ],
        totals: {
          subtotal: 3000.00,
          tax: 600.00,
          grand: 3600.00
        }
      }
    };
  }
  
  async function loadRecentInvoice() {
    try {
      loading.value = true;
      error.value = null;
  
      // Récupérer la liste des invoices et prendre la plus récente
      const response = await fetch('/api/invoices?page=1&pageSize=1&sort=createdDate:desc');
      if (!response.ok) {
        throw new Error('Failed to fetch recent invoice');
      }
      
      const data = await response.json();
      if (data.items && data.items.length > 0) {
        const invoice = data.items[0];
        currentData.value = transformInvoiceData(invoice);
      } else {
        throw new Error('No invoices found');
      }
    } catch (err: any) {
      console.error('Failed to load recent invoice:', err);
      error.value = err.message;
      // Fallback sur les données d'exemple
      currentData.value = getSampleData();
    } finally {
      loading.value = false;
    }
  }
  
  async function loadSpecificInvoice() {
    if (!specificInvoiceId.value.trim()) return;
  
    try {
      loading.value = true;
      error.value = null;
  
      const response = await fetch(`/api/invoices/${encodeURIComponent(specificInvoiceId.value)}`);
      if (!response.ok) {
        throw new Error('Invoice not found');
      }
      
      const invoice = await response.json();
      currentData.value = transformInvoiceData(invoice);
    } catch (err: any) {
      console.error('Failed to load specific invoice:', err);
      error.value = err.message;
    } finally {
      loading.value = false;
    }
  }
  
  function transformInvoiceData(rawInvoice: any) {
    // Transformer les données d'invoice du format de votre API vers le format attendu par le template
    return {
      invoice: {
        number: rawInvoice.finalNumber || rawInvoice.draftNumber || rawInvoice.number,
        issueDate: rawInvoice.createdDate || rawInvoice.issueDate,
        dueDate: rawInvoice.dueDate || new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
        status: rawInvoice.status,
        customer: {
          name: rawInvoice.customerName || rawInvoice.customer?.name,
          address: rawInvoice.customer?.address || '',
          city: rawInvoice.customer?.city || '',
          postalCode: rawInvoice.customer?.postalCode || '',
          country: rawInvoice.customer?.country || '',
          email: rawInvoice.customer?.email || '',
          phone: rawInvoice.customer?.phone || ''
        },
        facility: rawInvoice.facility || '',
        items: rawInvoice.items || [
          {
            position: 1,
            description: 'Service',
            qty: 1,
            price: rawInvoice.TotalAmount || rawInvoice.totalAmount || 0,
            amount: rawInvoice.TotalAmount || rawInvoice.totalAmount || 0
          }
        ],
        totals: {
          subtotal: rawInvoice.TotalAmount || rawInvoice.totalAmount || 0,
          tax: (rawInvoice.TotalAmount || rawInvoice.totalAmount || 0) * 0.2, // 20% TVA par défaut
          grand: (rawInvoice.TotalAmount || rawInvoice.totalAmount || 0) * 1.2
        }
      }
    };
  }
  
  async function updatePreviewData() {
    switch (previewMode.value) {
      case 'sample':
        currentData.value = getSampleData();
        break;
      case 'recent':
        await loadRecentInvoice();
        break;
      case 'specific':
        // Attendre que l'utilisateur saisisse un ID et clique sur Load
        break;
    }
  }
  
  async function refreshData() {
    await updatePreviewData();
  }
  
  function applyDataToTemplate() {
    if (!props.studio || !currentData.value) return;
  
    try {
      // Mettre à jour les datasources du studio
      const dataSourceManager = props.studio.DataSources.get('invoice-data');
      if (dataSourceManager) {
        dataSourceManager.records = [currentData.value];
      }
  
      // Déclencher un rafraîchissement du canvas
      if (props.studio.editor) {
        props.studio.editor.trigger('canvas:update');
      }
  
    } catch (err) {
      console.error('Failed to apply data to template:', err);
      error.value = t('invoiceDataSelector.errorApplyingData');
    }
  }
  
  // Initialiser avec des données d'exemple
  onMounted(() => {
    updatePreviewData();
  });
  
  // Surveiller les changements de studio
  watch(() => props.studio, (newStudio) => {
    if (newStudio && currentData.value) {
      applyDataToTemplate();
    }
  });
  </script>