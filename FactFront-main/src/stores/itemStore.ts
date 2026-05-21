import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import itemService from '../services/itemService'
import type { Item } from '../types/item'
import { persistState } from '../utils/persistState'

export interface ItemPaginationMetadata {
  currentPage: number
  pageSize: number
  totalItems: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface ItemFilters {
  search: string
  itemType: string
  status: string
  ownerId: string
}

const createInitialPagination = (): ItemPaginationMetadata => ({
  currentPage: 1,
  pageSize: 10,
  totalItems: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
})

export const useItemStore = defineStore('item', () => {
  // State
  const items = ref<Item[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const pagination = ref<ItemPaginationMetadata>(createInitialPagination())
  const filters = reactive<ItemFilters>({
    search: '',
    itemType: '',
    status: '',
    ownerId: '',
  })

  persistState({
    key: 'items',
    state: { filters },
    keys: ['filters'],
  })

  // Computed
  const totalCount = computed(() => pagination.value.totalItems)
  const hasItems = computed(() => items.value.length > 0)

  // Actions

  /**
   * Fetch a paginated list of items from the API using an injected axios instance.
   * The composable passes its $axios reference here because itemService uses the
   * standalone axios plugin while the composable historically used the injected one.
   */
  async function fetchItems(
    $axios: any,
    page: number = 1,
    size: number = 10,
  ) {
    loading.value = true
    error.value = null

    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('size', size.toString())
    params.append('expandLifecycles', 'true')

    if (filters.search?.trim()) params.append('search', filters.search.trim())
    if (filters.itemType?.trim()) params.append('itemType', filters.itemType.trim())
    if (filters.status?.trim()) params.append('status', filters.status.trim())
    if (filters.ownerId?.trim()) params.append('ownerId', filters.ownerId.trim())

    try {
      const response = await $axios.get(`/items?${params.toString()}`)

      if (response.data.items && response.data.pagination) {
        items.value = response.data.items as Item[]
        pagination.value = response.data.pagination
      } else if (Array.isArray(response.data)) {
        items.value = response.data as Item[]
        pagination.value = {
          currentPage: 1,
          pageSize: response.data.length,
          totalItems: response.data.length,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        }
      } else {
        items.value = []
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load items'
      items.value = []
      pagination.value = createInitialPagination()
    } finally {
      loading.value = false
    }
  }

  async function addItem($axios: any, payload: unknown) {
    // The composable owns form construction; the store just persists and refreshes.
    await $axios.post('/items', payload)
    await fetchItems($axios, pagination.value.currentPage, pagination.value.pageSize)
  }

  async function updateItem($axios: any, id: string, payload: unknown) {
    await $axios.put(`/items/${id}`, payload, {
      headers: { 'Content-Type': 'application/json' },
    })
    await fetchItems($axios, pagination.value.currentPage, pagination.value.pageSize)
  }

  async function deleteItem($axios: any, id: string) {
    await $axios.delete(`/items/${id}`)
    await fetchItems($axios, pagination.value.currentPage, pagination.value.pageSize)
  }

  function setFilters(newFilters: Partial<ItemFilters>) {
    Object.assign(filters, newFilters)
  }

  function resetFilters() {
    filters.search = ''
    filters.itemType = ''
    filters.status = ''
    filters.ownerId = ''
  }

  return {
    // State
    items,
    loading,
    error,
    pagination,
    filters,
    // Computed
    totalCount,
    hasItems,
    // Actions
    fetchItems,
    addItem,
    updateItem,
    deleteItem,
    setFilters,
    resetFilters,
  }
})
