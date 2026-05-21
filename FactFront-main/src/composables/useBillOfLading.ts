import { computed } from 'vue';
import type { BillOfLading } from '../services/billOfLadingService';
import { useBillOfLadingStore } from '../stores/billOfLadingStore';

// Re-export BillOfLading so existing imports from this composable still work
export type { BillOfLading };

/**
 * Thin wrapper around useBillOfLadingStore.
 *
 * All data state and async logic now live in the Pinia store.  This composable
 * exists solely to keep existing component call-sites unchanged: they import
 * useBillOfLading() and receive the same API surface they always did.
 */
export function useBillOfLading() {
  const store = useBillOfLadingStore();

  return {
    // State
    bills: computed(() => store.bills),
    pagination: computed(() => store.pagination),
    loading: store.loading,
    error: store.error,
    filters: store.filters,

    // Computed
    hasError: store.hasError,
    hasBills: store.hasBills,
    totalItems: store.totalItems,

    // Actions — delegate directly to store
    getBills: store.fetchBills,
    changePage: store.changePage,
    changePageSize: store.changePageSize,
    applyFilters: store.applyFilters,
    clearFilters: store.clearFilters,
    refreshBills: store.refreshBills,
    createBill: store.createBill,
    updateBill: store.updateBill,
    deleteBill: store.deleteBill,
    bulkImport: store.bulkImport,
  };
}
