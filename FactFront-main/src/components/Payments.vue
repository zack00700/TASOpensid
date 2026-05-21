<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  Search,
  Filter,
  RefreshCw,
  Plus,
  Eye,
  Trash2,
  Pencil,
  X,
  CreditCard,
  CheckCircle,
  AlertCircle,
  Clock,
} from 'lucide-vue-next';
import Pagination from './Pagination.vue';
import paymentService from '../services/paymentService';
import PageHeader from './ui/PageHeader.vue';
import Button from './ui/Button.vue';
import Select from './ui/Select.vue';
import Input from './ui/Input.vue';
import type { Payment, PaymentAllocation, PaymentMethod, PaymentStatus } from '../types/payment';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────

const payments = ref<Payment[]>([]);
const isLoading = ref(false);
const errorMessage = ref('');

// Filters
const filterStatus = ref('');
const filterCustomerName = ref('');

// Pagination
const currentPage = ref(1);
const pageSize = ref(50);
const totalItems = ref(0);
const totalPages = ref(1);

// Create/Edit modal
const showFormModal = ref(false);
const editingPayment = ref<Payment | null>(null);
const isSaving = ref(false);

// Detail modal
const showDetailModal = ref(false);
const selectedPayment = ref<Payment | null>(null);

// Reverse modal
const showReverseModal = ref(false);
const reversalReason = ref('');
const isReversing = ref(false);

// Allocation form
const showAllocationForm = ref(false);
const allocationForm = ref<PaymentAllocation>({ invoiceId: '', allocatedAmount: 0 });
const isAllocating = ref(false);

// ── Form state ──────────────────────────────────────────────────────────────

const emptyForm = (): Omit<Payment, 'id'> => ({
  customerName: '',
  amount: 0,
  currency: 'EUR',
  paymentMethod: 'WIRE_TRANSFER',
  paymentDate: new Date().toISOString().split('T')[0],
  bankReference: '',
  checkNumber: '',
  notes: '',
});

const formData = ref<Omit<Payment, 'id'>>(emptyForm());

// ── Data fetching ───────────────────────────────────────────────────────────

const fetchPayments = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const params: { status?: string; customerId?: string; page?: number; size?: number } = {
      page: currentPage.value,
      size: pageSize.value,
    };
    if (filterStatus.value) params.status = filterStatus.value;

    const data = await paymentService.getPayments(params);

    let items: Payment[];
    if (Array.isArray(data)) {
      items = data;
      totalItems.value = data.length;
      totalPages.value = 1;
    } else {
      items = data.content ?? data.items ?? [];
      totalItems.value = data.totalElements ?? data.totalItems ?? items.length;
      totalPages.value = data.totalPages ?? (Math.ceil(totalItems.value / pageSize.value) || 1);
    }

    // Client-side filter by customer name when API doesn't support it
    if (filterCustomerName.value) {
      const q = filterCustomerName.value.toLowerCase();
      payments.value = items.filter(p => p.customerName.toLowerCase().includes(q));
    } else {
      payments.value = items;
    }
  } catch (error) {
    errorMessage.value = t('payments.error.failedToLoad');
  } finally {
    isLoading.value = false;
  }
};

onMounted(fetchPayments);

// ── Pagination ─────────────────────────────────────────────────────────────

const paginationMeta = computed(() => ({
  currentPage: currentPage.value,
  pageSize: pageSize.value,
  totalItems: totalItems.value,
  totalPages: totalPages.value,
  hasNext: currentPage.value < totalPages.value,
  hasPrevious: currentPage.value > 1,
}));

const handlePageChange = (page: number) => {
  currentPage.value = page;
  fetchPayments();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchPayments();
};

// ── Filters ─────────────────────────────────────────────────────────────────

const applyFilters = () => {
  currentPage.value = 1;
  fetchPayments();
};

const clearFilters = () => {
  filterStatus.value = '';
  filterCustomerName.value = '';
  currentPage.value = 1;
  fetchPayments();
};

const hasActiveFilters = computed(() => filterStatus.value || filterCustomerName.value);

// ── Stats ───────────────────────────────────────────────────────────────────

const totalClearedAmount = computed(() =>
  payments.value
    .filter(p => p.status === 'CLEARED')
    .reduce((sum, p) => sum + p.amount, 0)
);

const pendingCount = computed(() =>
  payments.value.filter(p => p.status === 'PENDING').length
);

// ── Status badge ─────────────────────────────────────────────────────────────

const getStatusBadgeClasses = (status: PaymentStatus | undefined) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border';
  switch (status) {
    case 'PENDING':
      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'CLEARED':
      return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'FAILED':
      return `${base} bg-red-100 text-red-800 border-red-200`;
    case 'REVERSED':
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
    case 'CANCELLED':
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
    default:
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
  }
};

// ── Formatters ───────────────────────────────────────────────────────────────

const formatAmount = (amount: number, currency: string) => {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: 2 }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleDateString();
  } catch {
    return dateStr;
  }
};

const formatMethodLabel = (method: PaymentMethod) => {
  const labels: Record<PaymentMethod, string> = {
    WIRE_TRANSFER: t('payments.method.wireTransfer'),
    CHECK: t('payments.method.check'),
    CASH: t('payments.method.cash'),
    CREDIT_CARD: t('payments.method.creditCard'),
    DIRECT_DEBIT: t('payments.method.directDebit'),
    ACH: t('payments.method.ach'),
    CRYPTO: t('payments.method.crypto'),
    OTHER: t('payments.method.other'),
  };
  return labels[method] ?? method;
};

// ── Create / Edit modal ──────────────────────────────────────────────────────

const openCreate = () => {
  editingPayment.value = null;
  formData.value = emptyForm();
  showFormModal.value = true;
};

const openEdit = (payment: Payment) => {
  editingPayment.value = payment;
  formData.value = {
    customerName: payment.customerName,
    amount: payment.amount,
    currency: payment.currency,
    paymentMethod: payment.paymentMethod,
    paymentDate: payment.paymentDate,
    bankReference: payment.bankReference ?? '',
    checkNumber: payment.checkNumber ?? '',
    notes: payment.notes ?? '',
  };
  showFormModal.value = true;
};

const closeFormModal = () => {
  showFormModal.value = false;
  editingPayment.value = null;
};

const handleFormSubmit = async () => {
  if (!formData.value.customerName || !formData.value.paymentDate) {
    alert(t('payments.alert.customerNameAndDateRequired'));
    return;
  }
  isSaving.value = true;
  try {
    if (editingPayment.value?.id) {
      await paymentService.updatePayment(editingPayment.value.id, {
        ...formData.value,
        id: editingPayment.value.id,
      });
      alert(t('payments.alert.paymentUpdatedSuccess'));
    } else {
      await paymentService.createPayment(formData.value);
      alert(t('payments.alert.paymentCreatedSuccess'));
    }
    closeFormModal();
    await fetchPayments();
  } catch (error) {
    console.error('Failed to save payment:', error);
    alert(t('payments.alert.failedToSave'));
  } finally {
    isSaving.value = false;
  }
};

// ── Detail modal ─────────────────────────────────────────────────────────────

const openDetail = (payment: Payment) => {
  selectedPayment.value = payment;
  showDetailModal.value = true;
  showAllocationForm.value = false;
  allocationForm.value = { invoiceId: '', allocatedAmount: 0 };
};

const closeDetail = () => {
  showDetailModal.value = false;
  selectedPayment.value = null;
  showAllocationForm.value = false;
};

// ── Delete ────────────────────────────────────────────────────────────────────

const handleDelete = async (payment: Payment) => {
  if (!payment.id) return;
  if (!confirm(t('payments.confirm.deletePayment', { ref: payment.paymentReference ?? payment.id }))) return;
  try {
    await paymentService.deletePayment(payment.id);
    alert(t('payments.alert.paymentDeleted'));
    await fetchPayments();
  } catch (error) {
    console.error('Failed to delete payment:', error);
    alert(t('payments.alert.failedToDelete'));
  }
};

// ── Allocation ────────────────────────────────────────────────────────────────

const submitAllocation = async () => {
  if (!selectedPayment.value?.id) return;
  if (!allocationForm.value.invoiceId || allocationForm.value.allocatedAmount <= 0) {
    alert(t('payments.alert.invoiceIdAndAmountRequired'));
    return;
  }
  isAllocating.value = true;
  try {
    const updated = await paymentService.allocatePayment(selectedPayment.value.id, allocationForm.value);
    selectedPayment.value = updated;
    showAllocationForm.value = false;
    allocationForm.value = { invoiceId: '', allocatedAmount: 0 };
    await fetchPayments();
  } catch (error) {
    console.error('Failed to allocate payment:', error);
    alert(t('payments.alert.failedToAllocate'));
  } finally {
    isAllocating.value = false;
  }
};

// ── Reverse ───────────────────────────────────────────────────────────────────

const openReverse = () => {
  reversalReason.value = '';
  showReverseModal.value = true;
};

const closeReverse = () => {
  showReverseModal.value = false;
};

const submitReverse = async () => {
  if (!selectedPayment.value?.id) return;
  if (!reversalReason.value.trim()) {
    alert(t('payments.alert.reversalReasonRequired'));
    return;
  }
  isReversing.value = true;
  try {
    const updated = await paymentService.reversePayment(selectedPayment.value.id, reversalReason.value.trim());
    selectedPayment.value = updated;
    showReverseModal.value = false;
    await fetchPayments();
  } catch (error) {
    console.error('Failed to reverse payment:', error);
    alert(t('payments.alert.failedToReverse'));
  } finally {
    isReversing.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <PageHeader
      :title="t('nav.payments')"
      :subtitle="t('payments.subtitle', { cleared: formatAmount(totalClearedAmount, 'EUR'), pending: pendingCount, total: totalItems })"
      :count="totalItems"
    >
      <template #actions>
        <Button variant="secondary" :disabled="isLoading" @click="fetchPayments">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
          {{ t('invoiceDataSelector.button.refresh') }}
        </Button>
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          {{ t('payments.button.newPayment') }}
        </Button>
      </template>
    </PageHeader>

    <div class="px-6 py-6 space-y-6">

      <!-- Filter Bar -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
        <div class="p-6">
          <div class="flex flex-col sm:flex-row gap-4">
            <!-- Status filter -->
            <Select v-model="filterStatus" :label="t('invoices.filter.status')" class="sm:w-48">
              <option value="">{{ t('payments.filter.allStatuses') }}</option>
              <option value="PENDING">PENDING</option>
              <option value="CLEARED">CLEARED</option>
              <option value="FAILED">FAILED</option>
              <option value="REVERSED">REVERSED</option>
              <option value="CANCELLED">CANCELLED</option>
            </Select>

            <!-- Customer name search -->
            <Input
              v-model="filterCustomerName"
              :label="t('payments.filter.customerName')"
              :placeholder="t('payments.filter.searchByCustomerName')"
              :icon-leading="Search"
              class="flex-1"
              @enter="applyFilters"
            />

            <!-- Filter actions -->
            <div class="flex items-end gap-2">
              <button
                @click="applyFilters"
                class="inline-flex items-center px-4 py-2.5 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                <Filter class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.apply') }}
              </button>
              <button
                v-if="hasActiveFilters"
                @click="clearFilters"
                class="inline-flex items-center px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
              >
                <X class="h-4 w-4 mr-1.5" />
                {{ t('payments.button.clear') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Error Banner -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center space-x-3">
        <AlertCircle class="h-5 w-5 text-red-500 flex-shrink-0" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="ml-auto text-red-500 hover:text-red-700">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- Table Card -->
      <div class="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">

        <!-- Desktop table -->
        <div class="hidden sm:block overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th v-for="header in [t('payments.column.reference'), t('payments.column.customer'), t('payments.column.amount'), t('payments.column.method'), t('payments.column.paymentDate'), t('payments.column.status'), t('payments.column.unallocated'), '']"
                    :key="header"
                    class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
                >
                  {{ header }}
                </th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <!-- Loading skeleton -->
              <tr v-if="isLoading" v-for="i in 5" :key="i">
                <td v-for="j in 8" :key="j" class="px-4 py-3">
                  <div class="h-4 bg-gray-200 rounded animate-pulse w-3/4"></div>
                </td>
              </tr>

              <!-- Data rows -->
              <tr
                v-else
                v-for="payment in payments"
                :key="payment.id"
                class="hover:bg-gray-50 transition-colors duration-150 cursor-pointer"
                @click="openDetail(payment)"
              >
                <!-- Reference -->
                <td class="px-4 py-3">
                  <span class="text-sm font-mono text-gray-900">{{ payment.paymentReference || '—' }}</span>
                </td>

                <!-- Customer -->
                <td class="px-4 py-3">
                  <span class="text-sm font-medium text-gray-900">{{ payment.customerName }}</span>
                </td>

                <!-- Amount -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-900 font-medium">{{ formatAmount(payment.amount, payment.currency) }}</span>
                </td>

                <!-- Method -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">{{ formatMethodLabel(payment.paymentMethod) }}</span>
                </td>

                <!-- Payment Date -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-500 whitespace-nowrap">{{ formatDate(payment.paymentDate) }}</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClasses(payment.status)">{{ payment.status ?? '—' }}</span>
                </td>

                <!-- Unallocated -->
                <td class="px-4 py-3">
                  <span class="text-sm text-gray-700">
                    {{ payment.unallocatedAmount != null ? formatAmount(payment.unallocatedAmount, payment.currency) : '—' }}
                  </span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-3" @click.stop>
                  <div class="flex items-center space-x-1">
                    <button
                      @click="openDetail(payment)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('payments.action.viewDetails')"
                      :aria-label="t('payments.action.viewDetailsAria')"
                    >
                      <Eye class="h-4 w-4" />
                    </button>
                    <button
                      @click="openEdit(payment)"
                      class="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      :title="t('payments.action.edit')"
                      :aria-label="t('payments.action.editAria')"
                    >
                      <Pencil class="h-4 w-4" />
                    </button>
                    <button
                      v-if="payment.status === 'PENDING'"
                      @click="handleDelete(payment)"
                      class="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      :title="t('payments.action.delete')"
                      :aria-label="t('payments.action.deleteAria')"
                    >
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Mobile card list -->
        <div class="sm:hidden p-4">
          <div v-if="isLoading" class="space-y-3">
            <div v-for="i in 4" :key="i" class="bg-white rounded-xl border border-slate-200 p-4 animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-1/2 mb-2"></div>
              <div class="h-3 bg-gray-200 rounded w-3/4"></div>
            </div>
          </div>
          <div v-else-if="!payments.length" class="bg-white rounded-xl border border-slate-200 p-8 text-center">
            <p class="text-sm text-slate-500">{{ t('payments.empty.noPaymentsFound') }}</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="payment in payments"
              :key="payment.id"
              class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm cursor-pointer hover:border-blue-300 transition-colors"
              @click="openDetail(payment)"
            >
              <div class="flex items-start justify-between gap-2 mb-2">
                <div>
                  <p class="font-semibold text-slate-900 text-sm">{{ payment.customerName }}</p>
                  <p class="text-xs text-slate-500 mt-0.5 font-mono">{{ payment.paymentReference || '—' }}</p>
                </div>
                <span :class="getStatusBadgeClasses(payment.status)">{{ payment.status ?? '—' }}</span>
              </div>
              <div class="flex items-center justify-between text-xs text-slate-500 mt-1">
                <span class="font-medium text-slate-700">{{ formatAmount(payment.amount, payment.currency) }}</span>
                <span>{{ formatDate(payment.paymentDate) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state (desktop) -->
        <div v-if="!isLoading && payments.length === 0" class="hidden sm:block text-center py-12">
          <CreditCard class="mx-auto h-12 w-12 text-gray-400" />
          <h3 class="mt-2 text-sm font-medium text-gray-900">{{ t('payments.empty.title') }}</h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ hasActiveFilters ? t('payments.empty.descriptionFiltered') : t('payments.empty.description') }}
          </p>
          <div class="mt-6 flex items-center justify-center space-x-3">
            <button
              v-if="hasActiveFilters"
              @click="clearFilters"
              class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              {{ t('payments.button.clearFilters') }}
            </button>
            <button
              v-else
              @click="openCreate"
              class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
            >
              <Plus class="h-4 w-4 mr-2" />
              {{ t('payments.button.newPayment') }}
            </button>
          </div>
        </div>

        <!-- Pagination -->
        <Pagination
          v-if="totalItems > 0"
          :pagination="paginationMeta"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- ── Create / Edit Modal ──────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showFormModal"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeFormModal"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] flex flex-col">

          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div>
              <h3 class="text-lg font-semibold text-gray-900">
                {{ editingPayment ? t('payments.modal.form.editTitle') : t('payments.modal.form.newTitle') }}
              </h3>
              <p class="text-sm text-gray-500 mt-0.5">
                {{ editingPayment ? t('payments.modal.form.editSubtitle') : t('payments.modal.form.newSubtitle') }}
              </p>
            </div>
            <button @click="closeFormModal" class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Form Body -->
          <div class="overflow-y-auto flex-1 p-6 space-y-4">

            <!-- Customer Name -->
            <Input
              v-model="formData.customerName"
              :label="t('payments.form.customerName')"
              :placeholder="t('payments.form.customerNamePlaceholder')"
              required
            />

            <!-- Amount + Currency -->
            <div class="grid grid-cols-2 gap-4">
              <Input
                v-model.number="formData.amount"
                :label="t('payments.form.amount')"
                type="number"
                placeholder="0.00"
                required
                inputmode="decimal"
              />
              <Select v-model="formData.currency" :label="t('payments.form.currency')" required>
                <option value="EUR">EUR</option>
                <option value="USD">USD</option>
                <option value="GBP">GBP</option>
              </Select>
            </div>

            <!-- Payment Method -->
            <Select v-model="formData.paymentMethod" :label="t('payments.form.paymentMethod')" required>
              <option value="WIRE_TRANSFER">{{ t('payments.method.wireTransfer') }}</option>
              <option value="CHECK">{{ t('payments.method.check') }}</option>
              <option value="CASH">{{ t('payments.method.cash') }}</option>
              <option value="CREDIT_CARD">{{ t('payments.method.creditCard') }}</option>
              <option value="DIRECT_DEBIT">{{ t('payments.method.directDebit') }}</option>
              <option value="ACH">{{ t('payments.method.ach') }}</option>
              <option value="CRYPTO">{{ t('payments.method.crypto') }}</option>
              <option value="OTHER">{{ t('payments.method.other') }}</option>
            </Select>

            <!-- Payment Date -->
            <Input
              v-model="formData.paymentDate"
              :label="t('payments.form.paymentDate')"
              type="date"
              required
            />

            <!-- Bank Reference -->
            <Input
              v-model="formData.bankReference"
              :label="t('payments.form.bankReference')"
              :placeholder="t('payments.form.bankReferencePlaceholder')"
            />

            <!-- Check Number (only for CHECK method) -->
            <Input
              v-if="formData.paymentMethod === 'CHECK'"
              v-model="formData.checkNumber"
              :label="t('payments.form.checkNumber')"
              :placeholder="t('payments.form.checkNumberPlaceholder')"
            />

            <!-- Notes -->
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('payments.form.notes') }}</label>
              <textarea
                v-model="formData.notes"
                rows="3"
                :placeholder="t('payments.form.notesPlaceholder')"
                class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 resize-none"
              ></textarea>
            </div>
          </div>

          <!-- Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex justify-end space-x-3">
            <button
              @click="closeFormModal"
              :disabled="isSaving"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="handleFormSubmit"
              :disabled="isSaving"
              class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
            >
              <RefreshCw v-if="isSaving" class="h-4 w-4 mr-2 animate-spin" />
              {{ isSaving ? t('payments.button.saving') : (editingPayment ? t('payments.button.saveChanges') : t('payments.button.createPayment')) }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── Detail Modal ──────────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showDetailModal && selectedPayment"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-50 p-4"
        @click.self="closeDetail"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">

          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200">
            <div class="flex items-center space-x-3">
              <CreditCard class="h-6 w-6 text-blue-600" />
              <div>
                <h3 class="text-lg font-semibold text-gray-900">{{ t('payments.modal.detail.title') }}</h3>
                <p class="text-xs text-gray-500 font-mono mt-0.5">{{ selectedPayment.id }}</p>
              </div>
            </div>
            <button @click="closeDetail" class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors">
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body -->
          <div class="overflow-y-auto flex-1 p-6 space-y-6">

            <!-- Key fields grid -->
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.reference') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900">{{ selectedPayment.paymentReference || '—' }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.customer') }}</p>
                <p class="mt-1 text-sm font-medium text-gray-900">{{ selectedPayment.customerName }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.status') }}</p>
                <div class="mt-1">
                  <span :class="getStatusBadgeClasses(selectedPayment.status)">{{ selectedPayment.status ?? '—' }}</span>
                </div>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.amount') }}</p>
                <p class="mt-1 text-sm font-medium text-gray-900">{{ formatAmount(selectedPayment.amount, selectedPayment.currency) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.unallocated') }}</p>
                <p class="mt-1 text-sm text-gray-900">
                  {{ selectedPayment.unallocatedAmount != null ? formatAmount(selectedPayment.unallocatedAmount, selectedPayment.currency) : '—' }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.method') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatMethodLabel(selectedPayment.paymentMethod) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.paymentDate') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedPayment.paymentDate) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.receivedDate') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ formatDate(selectedPayment.receivedDate) }}</p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.bankReference') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900">{{ selectedPayment.bankReference || '—' }}</p>
              </div>
              <div v-if="selectedPayment.checkNumber">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.checkNumber') }}</p>
                <p class="mt-1 text-sm font-mono text-gray-900">{{ selectedPayment.checkNumber }}</p>
              </div>
              <div v-if="selectedPayment.processedBy">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.processedBy') }}</p>
                <p class="mt-1 text-sm text-gray-900">{{ selectedPayment.processedBy }}</p>
              </div>
            </div>

            <!-- Notes -->
            <div v-if="selectedPayment.notes">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{{ t('payments.field.notes') }}</p>
              <div class="bg-gray-50 border border-gray-200 rounded-lg p-3 text-sm text-gray-700">
                {{ selectedPayment.notes }}
              </div>
            </div>

            <!-- Reversal Reason -->
            <div v-if="selectedPayment.reversalReason">
              <p class="text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{{ t('payments.field.reversalReason') }}</p>
              <div class="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-800">
                {{ selectedPayment.reversalReason }}
              </div>
            </div>

            <!-- Allocations table -->
            <div>
              <div class="flex items-center justify-between mb-3">
                <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('payments.field.allocations') }}</p>
                <button
                  @click="showAllocationForm = !showAllocationForm"
                  class="inline-flex items-center px-3 py-1.5 border border-transparent text-xs font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700"
                >
                  <Plus class="h-3.5 w-3.5 mr-1" />
                  {{ t('payments.button.allocateToInvoice') }}
                </button>
              </div>

              <!-- Allocation form -->
              <div v-if="showAllocationForm" class="mb-4 bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('payments.form.invoiceId') }} <span class="text-red-500">*</span></label>
                    <input
                      v-model="allocationForm.invoiceId"
                      type="text"
                      :placeholder="t('payments.form.invoiceIdPlaceholder')"
                      class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </div>
                  <div>
                    <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('payments.form.amount') }} <span class="text-red-500">*</span></label>
                    <input
                      v-model.number="allocationForm.allocatedAmount"
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="0.00"
                      class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
                    />
                  </div>
                </div>
                <div class="flex justify-end space-x-2">
                  <button
                    @click="showAllocationForm = false"
                    class="px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-50"
                  >
                    {{ t('common.cancel') }}
                  </button>
                  <button
                    @click="submitAllocation"
                    :disabled="isAllocating"
                    class="inline-flex items-center px-3 py-1.5 border border-transparent rounded-lg text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
                  >
                    <RefreshCw v-if="isAllocating" class="h-3 w-3 mr-1 animate-spin" />
                    {{ isAllocating ? t('payments.button.allocating') : t('payments.button.addAllocation') }}
                  </button>
                </div>
              </div>

              <!-- Allocations table -->
              <div v-if="selectedPayment.allocations?.length" class="border border-gray-200 rounded-lg overflow-hidden">
                <table class="min-w-full divide-y divide-gray-200">
                  <thead class="bg-gray-50">
                    <tr>
                      <th class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{{ t('payments.column.alloc.invoiceId') }}</th>
                      <th class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{{ t('payments.column.alloc.invoiceNumber') }}</th>
                      <th class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{{ t('payments.column.alloc.amount') }}</th>
                      <th class="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{{ t('payments.column.alloc.date') }}</th>
                    </tr>
                  </thead>
                  <tbody class="bg-white divide-y divide-gray-200">
                    <tr v-for="alloc in selectedPayment.allocations" :key="alloc.allocationId ?? alloc.invoiceId">
                      <td class="px-3 py-2 text-xs font-mono text-gray-900">{{ alloc.invoiceId }}</td>
                      <td class="px-3 py-2 text-xs text-gray-700">{{ alloc.invoiceNumber || '—' }}</td>
                      <td class="px-3 py-2 text-xs text-gray-900 font-medium">{{ formatAmount(alloc.allocatedAmount, selectedPayment.currency) }}</td>
                      <td class="px-3 py-2 text-xs text-gray-500">{{ formatDate(alloc.allocationDate) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="text-center py-6 bg-gray-50 rounded-lg border border-gray-200">
                <p class="text-sm text-gray-500">{{ t('payments.empty.noAllocations') }}</p>
              </div>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex items-center justify-between">
            <div>
              <button
                v-if="selectedPayment.status === 'CLEARED'"
                @click="openReverse"
                class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                {{ t('payments.button.reversePayment') }}
              </button>
            </div>
            <button
              @click="closeDetail"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {{ t('common.close') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ── Reverse Modal ─────────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showReverseModal"
        class="fixed inset-0 bg-gray-900 bg-opacity-50 flex items-center justify-center z-[60] p-4"
        @click.self="closeReverse"
      >
        <div class="bg-white rounded-xl shadow-xl w-full max-w-md">
          <div class="p-6 border-b border-gray-200">
            <h3 class="text-lg font-semibold text-gray-900">{{ t('payments.modal.reverse.title') }}</h3>
            <p class="text-sm text-gray-500 mt-0.5">{{ t('payments.modal.reverse.subtitle') }}</p>
          </div>
          <div class="p-6">
            <label class="block text-sm font-medium text-gray-700 mb-1">{{ t('payments.field.reversalReason') }} <span class="text-red-500">*</span></label>
            <textarea
              v-model="reversalReason"
              rows="3"
              :placeholder="t('payments.modal.reverse.placeholder')"
              class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 resize-none"
            ></textarea>
          </div>
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl border-t border-gray-200 flex justify-end space-x-3">
            <button
              @click="closeReverse"
              :disabled="isReversing"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              @click="submitReverse"
              :disabled="isReversing"
              class="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 disabled:opacity-50"
            >
              <RefreshCw v-if="isReversing" class="h-4 w-4 mr-2 animate-spin" />
              {{ isReversing ? t('payments.button.reversing') : t('payments.button.confirmReversal') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
