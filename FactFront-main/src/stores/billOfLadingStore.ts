import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import billOfLadingService, {
  type BillOfLading,
  type PaginationParams,
  type PaginationMetadata,
  type PagedResponse,
  fetchPaginated,
} from '../services/billOfLadingService'
import { persistState } from '../utils/persistState'

export interface BillOfLadingFilters {
  search: string
  status: string
  shipper: string
  vessel: string
  transportType: string
}

const createInitialPagination = (): PaginationMetadata => ({
  currentPage: 1,
  pageSize: 10,
  totalItems: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
})

export const useBillOfLadingStore = defineStore('billOfLading', () => {
  // ── State ──────────────────────────────────────────────────────────────────
  const bills = ref<BillOfLading[]>([])
  const pagination = ref<PaginationMetadata>(createInitialPagination())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const filters = reactive<BillOfLadingFilters>({
    search: '',
    status: '',
    shipper: '',
    vessel: '',
    transportType: '',
  })

  persistState({
    key: 'bol',
    state: { filters },
    keys: ['filters'],
  })

  // ── Computed ───────────────────────────────────────────────────────────────
  const isLoading = computed(() => loading.value)
  const hasError = computed(() => !!error.value)
  const hasBills = computed(() => bills.value.length > 0)
  const totalItems = computed(() => pagination.value?.totalItems || 0)
  const currentFilters = computed(() => ({ ...filters }))

  // ── Actions ────────────────────────────────────────────────────────────────

  async function fetchBills(page: number = 1, size: number = 10) {
    loading.value = true
    error.value = null

    try {
      const params: PaginationParams = {
        page,
        size,
        search: filters.search || undefined,
        status: filters.status || undefined,
        shipper: filters.shipper || undefined,
        vessel: filters.vessel || undefined,
        transportType: filters.transportType || undefined,
      }

      const response: PagedResponse<BillOfLading> = await fetchPaginated(params)

      bills.value = response.items
      pagination.value = {
        currentPage: response.pagination.currentPage,
        pageSize: response.pagination.pageSize,
        totalItems: response.pagination.totalItems,
        totalPages: response.pagination.totalPages,
        hasNext: response.pagination.hasNext,
        hasPrevious: response.pagination.hasPrevious,
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch bills of lading'
      bills.value = []
      pagination.value = createInitialPagination()
    } finally {
      loading.value = false
    }
  }

  async function changePage(page: number) {
    if (page === pagination.value.currentPage) return
    await fetchBills(page, pagination.value.pageSize)
  }

  async function changePageSize(size: number) {
    if (size === pagination.value.pageSize) return
    await fetchBills(1, size)
  }

  async function applyFilters(newFilters: Partial<BillOfLadingFilters>) {
    Object.assign(filters, newFilters)
    await fetchBills(1, pagination.value.pageSize)
  }

  async function clearFilters() {
    filters.search = ''
    filters.status = ''
    filters.shipper = ''
    filters.vessel = ''
    filters.transportType = ''
    await fetchBills(1, pagination.value.pageSize)
  }

  async function refreshBills() {
    await fetchBills(pagination.value.currentPage, pagination.value.pageSize)
  }

  async function createBill(billData: Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>) {
    await billOfLadingService.create(billData)
    await refreshBills()
  }

  async function updateBill(
    id: string,
    billData: Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>,
  ) {
    await billOfLadingService.update(id, billData)
    await refreshBills()
  }

  async function deleteBill(id: string) {
    await billOfLadingService.delete(id)
    await refreshBills()
  }

  async function bulkImport(data: unknown[]) {
    const result = await billOfLadingService.bulkImport(data)
    await refreshBills()
    return result
  }

  return {
    // State
    bills,
    pagination,
    loading: isLoading,
    error,
    filters: currentFilters,
    // Computed
    hasError,
    hasBills,
    totalItems,
    // Actions
    fetchBills,
    changePage,
    changePageSize,
    applyFilters,
    clearFilters,
    refreshBills,
    createBill,
    updateBill,
    deleteBill,
    bulkImport,
  }
})
