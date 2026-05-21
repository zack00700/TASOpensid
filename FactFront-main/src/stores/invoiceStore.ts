import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import type { Invoice } from '../types/invoice'
import invoiceService from '../services/invoiceService'
import { persistState } from '../utils/persistState'

// Re-export filter/sort types so components can import from one place
export interface InvoiceFilters {
  status: string[]
  customerName: string
  facility: string
  draftNumber: string
  finalNumber: string
  createdDateFrom: string | null
  createdDateTo: string | null
}

export interface InvoicePaginationState {
  page: number
  pageSize: number
  totalCount: number
  sort: string
}

export const SORT_FIELD_MAP: Record<string, string> = {
  createdDate: 'createdDate',
  TotalAmount: 'TotalAmount',
  customerName: 'customerName',
  facility: 'facility',
  status: 'status',
  draftNumber: 'draftNumber',
  finalNumber: 'finalNumber',
}

export const SORTABLE_FIELDS = Object.keys(SORT_FIELD_MAP)

const DEFAULT_SORT = 'createdDate:desc'

const defaultFilters = (): InvoiceFilters => ({
  status: [],
  customerName: '',
  facility: '',
  draftNumber: '',
  finalNumber: '',
  createdDateFrom: null,
  createdDateTo: null,
})

const API_TO_UI_SORT_FIELD_MAP = Object.fromEntries(
  Object.entries(SORT_FIELD_MAP).map(([ui, api]) => [api, ui]),
)

function normalizeSort(value: string): string {
  if (!value) return DEFAULT_SORT
  let normalized = value.replace(',', ':')
  const parts = normalized.split(':')
  let changed = normalized !== value

  let field = parts[0]
  let dir = parts[1]

  if (!dir) {
    changed = true
    dir = 'asc'
  }

  dir = dir.toLowerCase()
  if (dir !== 'asc' && dir !== 'desc') {
    changed = true
    dir = 'asc'
  }

  const mappedField = API_TO_UI_SORT_FIELD_MAP[field]
  if (mappedField && mappedField !== field) {
    field = mappedField
    changed = true
  }

  if (!SORTABLE_FIELDS.includes(field)) {
    changed = true
    const [defaultField, defaultDir] = DEFAULT_SORT.split(':')
    field = defaultField
    dir = defaultDir
  }

  normalized = `${field}:${dir}`
  if (changed) console.warn(`Malformed sort "${value}" corrected to "${normalized}"`)
  return normalized
}

export const useInvoiceStore = defineStore('invoice', () => {
  // ── State ──────────────────────────────────────────────────────────────────
  const items = ref<Invoice[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const totalCount = ref(0)
  const totalAmount = ref(0)
  const statusCounts = ref<Record<string, number>>({ DRAFT: 0, FINAL: 0 })
  const statusAmounts = ref<Record<string, number>>({ DRAFT: 0, FINAL: 0 })
  const trends = ref<{ totalAmount: number; status: Record<string, number>; displayed: number }>({
    totalAmount: 0,
    status: { DRAFT: 0, FINAL: 0 },
    displayed: 0,
  })
  const updatedAt = ref<Date | null>(null)

  const pagination = reactive<InvoicePaginationState>({
    page: 1,
    pageSize: 50,
    totalCount: 0,
    sort: DEFAULT_SORT,
  })

  const filters = reactive<InvoiceFilters>(defaultFilters())

  // Persist pagination + filters across F5.  URL params still win on hydrate,
  // so shareable links keep precedence over locally stored state.
  persistState({
    key: 'invoices',
    state: { pagination, filters },
    keys: ['pagination', 'filters'],
  })

  // ── Computed ───────────────────────────────────────────────────────────────
  const displayedCount = computed(() => items.value.length)

  // ── Helpers ────────────────────────────────────────────────────────────────
  /**
   * The store needs an axios instance to call GET /invoices because that
   * endpoint is not covered by the standalone invoiceService (which only wraps
   * mutation endpoints).  Components/composables pass the injected $axios when
   * calling fetchInvoices.
   */
  function buildParams(axiosInstance?: unknown) {
    pagination.sort = normalizeSort(pagination.sort)
    const [field, dir] = pagination.sort.split(':')
    const apiField = SORT_FIELD_MAP[field] || field
    const params: Record<string, string> = {
      page: String(pagination.page),
      pageSize: String(pagination.pageSize),
      sort: `${apiField}:${dir}`,
      includePayments: 'true',
    }

    const f = filters
    if (f.status.length) params.status = f.status.join(',')
    if (f.customerName) params.customerName = f.customerName
    if (f.facility) params.facility = f.facility
    if (f.draftNumber) params.draftNumber = f.draftNumber
    if (f.finalNumber) params.finalNumber = f.finalNumber
    if (f.createdDateFrom) params.createdDateFrom = f.createdDateFrom
    if (f.createdDateTo) params.createdDateTo = f.createdDateTo
    return params
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  /**
   * Fetch invoices.  Requires the injected axios instance because the GET
   * /invoices list endpoint is not exposed through invoiceService.
   */
  async function fetchInvoices($axios: { get: (url: string, cfg?: unknown) => Promise<{ data: unknown }> }) {
    loading.value = true
    error.value = null

    try {
      const params = buildParams()
      const { data } = await $axios.get('/invoices', { params }) as { data: Record<string, unknown> }

      items.value = ((data.items as Record<string, unknown>[] | undefined) || []).map(
        (inv: Record<string, unknown>) => ({
          ...inv,
          id: (inv.id ?? inv.invoiceId) as string,
          amount: Number(inv.totalAmount ?? inv.TotalAmount ?? inv.amount ?? 0),
        }),
      ) as Invoice[]

      totalCount.value = (data.totalCount as number) || 0
      pagination.totalCount = totalCount.value
      totalAmount.value = ((data.aggregates as Record<string, unknown> | undefined)?.totalAmount as number) || 0

      statusCounts.value =
        (data.statusCounts as Record<string, number>) ||
        items.value.reduce(
          (acc: Record<string, number>, inv: Invoice) => {
            acc[inv.status] = (acc[inv.status] || 0) + 1
            return acc
          },
          { DRAFT: 0, FINAL: 0 },
        )

      statusAmounts.value =
        (data.statusAmounts as Record<string, number>) ||
        items.value.reduce(
          (acc: Record<string, number>, inv: Invoice) => {
            acc[inv.status] = (acc[inv.status] || 0) + (inv.amount ?? 0)
            return acc
          },
          { DRAFT: 0, FINAL: 0 },
        )

      trends.value =
        (data.trends as typeof trends.value) ||
        { totalAmount: 0, status: { DRAFT: 0, FINAL: 0 }, displayed: 0 }

      updatedAt.value = new Date()
    } catch (err: unknown) {
      let message = 'Failed to fetch invoices'
      if (err instanceof Error && 'response' in err) {
        const axiosErr = err as Error & { response?: { status: number; data: unknown } }
        const resp = axiosErr.response
        if (resp) {
          const respData = resp.data as Record<string, unknown> | string | null | undefined
          if (respData && typeof respData === 'object') {
            message =
              (respData.message as string) ||
              (respData.error as string) ||
              message
            if (!message && Array.isArray(respData.errors))
              message = (respData.errors as string[]).join(', ')
          } else if (typeof respData === 'string') {
            message = respData || message
          }
          if (resp.status === 400) console.error('fetchInvoices 400 error', resp)
        }
      } else if (err instanceof Error) {
        message = err.message
      }

      error.value = message
      items.value = []
      totalCount.value = 0
      pagination.totalCount = 0
      totalAmount.value = 0
      statusCounts.value = { DRAFT: 0, FINAL: 0 }
      statusAmounts.value = { DRAFT: 0, FINAL: 0 }
      trends.value = { totalAmount: 0, status: { DRAFT: 0, FINAL: 0 }, displayed: 0 }
    } finally {
      loading.value = false
    }
  }

  /** Finalize an invoice via invoiceService and refresh the list. */
  async function finalizeInvoice(
    invoiceId: string,
    $axios: { get: (url: string, cfg?: unknown) => Promise<{ data: unknown }> },
  ) {
    await invoiceService.finalize(invoiceId)
    await fetchInvoices($axios)
  }

  /** Delete an invoice via invoiceService and refresh the list. */
  async function deleteInvoice(
    invoiceId: string,
    $axios: { get: (url: string, cfg?: unknown) => Promise<{ data: unknown }> },
  ) {
    await invoiceService.delete(invoiceId)
    await fetchInvoices($axios)
  }

  /** Generate a draft invoice for a bill of lading. */
  async function generateDraft(
    blId: string,
    customerName: string,
    $axios: { get: (url: string, cfg?: unknown) => Promise<{ data: unknown }> },
  ) {
    await invoiceService.generateDraft(blId, customerName)
    await fetchInvoices($axios)
  }

  /** Hydrate pagination/filter state from URL search string. */
  function hydrateFromUrl(search: string = window.location.search) {
    const params = new URLSearchParams(search)
    if (params.get('status')) filters.status = params.get('status')!.split(',')
    filters.customerName = params.get('customerName') || ''
    filters.facility = params.get('facility') || ''
    filters.draftNumber = params.get('draftNumber') || ''
    filters.finalNumber = params.get('finalNumber') || ''
    filters.createdDateFrom = params.get('createdDateFrom')
    filters.createdDateTo = params.get('createdDateTo')
    pagination.page = Number(params.get('page') || '1')
    pagination.pageSize = Number(
      params.get('pageSize') || params.get('size') || pagination.pageSize,
    )
    const sortParam = params.get('sort')
    if (sortParam) pagination.sort = normalizeSort(sortParam)
  }

  /** Reset filters and jump back to page 1. */
  function clearFilters() {
    Object.assign(filters, defaultFilters())
    pagination.page = 1
  }

  return {
    // State
    items,
    loading,
    error,
    totalCount,
    totalAmount,
    statusCounts,
    statusAmounts,
    trends,
    updatedAt,
    pagination,
    filters,
    // Computed
    displayedCount,
    // Actions
    fetchInvoices,
    finalizeInvoice,
    deleteInvoice,
    generateDraft,
    hydrateFromUrl,
    clearFilters,
  }
})
