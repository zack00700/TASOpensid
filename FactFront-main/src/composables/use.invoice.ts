import { inject, computed } from 'vue';
import type { AxiosInstance } from 'axios';
import { useInvoiceStore, SORT_FIELD_MAP, SORTABLE_FIELDS } from '../stores/invoiceStore';

// Re-export types so existing imports from this composable continue to work
export type { InvoiceFilters } from '../stores/invoiceStore';
export { SORT_FIELD_MAP, SORTABLE_FIELDS };

/**
 * Thin wrapper around useInvoiceStore.
 *
 * All data state and async logic now live in the Pinia store.  This composable
 * exists solely to keep existing component call-sites unchanged: they import
 * useInvoice() and receive the same { state, displayedCount, fetchInvoices,
 * clearFilters, hydrateFromUrl } shape they always did.
 */
export function useInvoice() {
  const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
  const store = useInvoiceStore();

  // Build a reactive "state" proxy that matches the shape components depend on
  const state = {
    get filters() { return store.filters; },
    get page() { return store.pagination.page; },
    set page(v: number) { store.pagination.page = v; },
    get pageSize() { return store.pagination.pageSize; },
    set pageSize(v: number) { store.pagination.pageSize = v; },
    get sort() { return store.pagination.sort; },
    set sort(v: string) { store.pagination.sort = v; },
    get items() { return store.items; },
    get totalCount() { return store.totalCount; },
    get totalAmount() { return store.totalAmount; },
    get statusCounts() { return store.statusCounts; },
    get statusAmounts() { return store.statusAmounts; },
    get trends() { return store.trends; },
    get loading() { return store.loading; },
    get error() { return store.error; },
    get updatedAt() { return store.updatedAt; },
  };

  const displayedCount = computed(() => store.displayedCount);

  async function fetchInvoices() {
    await store.fetchInvoices($axios);
  }

  function clearFilters() {
    store.clearFilters();
  }

  function hydrateFromUrl(search?: string) {
    store.hydrateFromUrl(search);
  }

  return {
    state,
    displayedCount,
    fetchInvoices,
    clearFilters,
    hydrateFromUrl,
  };
}
