import { inject, onBeforeMount, ref, computed } from 'vue';
import type { AxiosInstance } from 'axios';
import { Item, ItemFormData } from '../types/item';
import { useItemStore } from '../stores/itemStore';

/** Minimal shape of an Axios-style error used for diagnostic logging. */
interface AxiosLikeError {
  message?: string;
  code?: string;
  response?: { status?: number; data?: unknown };
  request?: unknown;
  stack?: string;
  config?: { baseURL?: string; timeout?: number };
}


export function useItem() {
    const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;

    // ── Delegate shared data state to the Pinia store ──────────────────────
    const store = useItemStore();

    // Expose store slices under the same names components expect
    const items = computed(() => store.items as Item[] | null);
    const loading = computed(() => store.loading);
    const pagination = computed(() => store.pagination);
    const filters = store.filters; // reactive object — mutations propagate to store

    // Your existing form data (preserved exactly)
    const formData = ref<ItemFormData>({
        _id: "",
        itemNumber: "",
        itemType: "container",
        type: "",
        ownerId: "",
        position: "",
        status: "Available",
        lastInspection: "",
        nextInspection: "",
        notes: "",
        lifeCycles: [],
    });

    // Your existing state (preserved)
    const errors = ref<Record<string, string>>({});

    // Your existing getItems function (modified to use pagination by default)
    async function getItems() {
        // Always use pagination now
        await getItemsPaginated(1, 10);
    }

    // Delegates to the Pinia store; all logging retained via the store's fetchItems.
    async function getItemsPaginated(page: number = 1, size: number = 10) {
        await store.fetchItems($axios, page, size);
    }

    // NEW: Pagination methods — delegate to the store
    const changePage = async (page: number) => {
        if (page === store.pagination.currentPage) return;
        await store.fetchItems($axios, page, store.pagination.pageSize);
    };

    const changePageSize = async (size: number) => {
        if (size === store.pagination.pageSize) return;
        await store.fetchItems($axios, 1, size);
    };

    const applyFilters = async (newFilters: Partial<typeof filters>) => {
        store.setFilters(newFilters);
        await store.fetchItems($axios, 1, store.pagination.pageSize);
    };

    const clearFilters = async () => {
        store.resetFilters();
        await store.fetchItems($axios, 1, store.pagination.pageSize);
    };

    const refreshItems = async () => {
        await store.fetchItems($axios, store.pagination.currentPage, store.pagination.pageSize);
    };

    // Your existing validateForm function (preserved exactly)
    const validateForm = () => {
        errors.value = {};
        let isValid = true;

        if (!formData.value.itemNumber) {
            errors.value.itemNumber = "Item number is required";
            isValid = false;
        }

        if (!formData.value.type) {
            errors.value.type = "Type is required";
            isValid = false;
        }

        if (!formData.value.ownerId) {
            errors.value.ownerId = "Owner ID is required";
            isValid = false;
        }

        if (formData.value.lastInspection && formData.value.nextInspection) {
            const last = new Date(formData.value.lastInspection);
            const next = new Date(formData.value.nextInspection);
            if (next <= last) {
                errors.value.nextInspection = "Next inspection date must be after last inspection";
                isValid = false;
            }
        }

        return isValid;
    };

    // Your existing addItem function — delegates to store for persistence + refresh
    async function addItem() {
        try {
            await store.addItem($axios, formData.value);
        } catch (error) {
            console.error(error);
        }
    }

    async function updateItem() {
        console.log("=== UPDATE ITEM DEBUG ===");
        console.log("Form data:", formData.value);
        console.log("Item ID:", formData.value._id);

        if (!formData.value._id) {
            console.error("No _id found in form data!");
            throw new Error("Item ID is required for updating");
        }

        console.log("Item ID found:", formData.value._id);

        const parseDate = (dateString: string): string | null => {
            if (!dateString || !dateString.trim()) return null;
            try {
                const date = new Date(dateString + 'T00:00:00.000Z');
                if (isNaN(date.getTime())) return null;
                return date.toISOString();
            } catch (error) {
                console.warn('Invalid date string:', dateString);
                return null;
            }
        };

        const mapItemType = (frontendType: string): string => {
            const mapping: Record<string, string> = {
                'container': 'CONTAINER',
                'breakbulk': 'BREAKBULK',
                'vehicle': 'VEHICLE',
            };
            return mapping[frontendType.toLowerCase()] || frontendType.toUpperCase();
        };

        const payload = {
            id: formData.value._id,
            itemNumber: formData.value.itemNumber,
            itemType: mapItemType(formData.value.itemType),
            type: formData.value.type,
            ownerId: formData.value.ownerId,
            position: formData.value.position,
            status: formData.value.status,
            lastInspectionDate: parseDate(formData.value.lastInspection),
            nextInspectionDate: parseDate(formData.value.nextInspection),
            notes: formData.value.notes,
            lifeCycles: formData.value.lifeCycles || [],
        };

        console.log("PUT payload:", payload);

        try {
            await store.updateItem($axios, formData.value._id, payload);
        } catch (error: unknown) {
            console.error("updateItem error:", error);
            console.error("Error response:", (error as AxiosLikeError).response);
            throw error;
        }
    }

    async function draftInvoice(itemId: string[], customer: string): Promise<{ invoiceId: string; previewUrl: string }> {
        const res = await $axios.post('/invoice/' + itemId + '/draft/customer/' + customer);
        return res.data as { invoiceId: string; previewUrl: string };
    }

    // NEW: Computed properties for pagination — backed by store
    const hasItems = computed(() => store.hasItems);
    const totalItems = computed(() => store.totalCount);
    const isLoading = computed(() => store.loading);

    // Updated onBeforeMount to use pagination with 10 items by default
    onBeforeMount(() => {
        getItemsPaginated(1, 10);
    });

    return {
        // Your existing exports (preserved)
        items,
        formData,
        errors,
        validateForm,
        addItem,
        updateItem,
        draftInvoice,
        getItems,

        // NEW: Pagination exports
        pagination,
        loading: isLoading,
        filters: computed(() => ({ ...filters })),
        hasItems,
        totalItems,

        // NEW: Pagination methods
        getItemsPaginated,
        changePage,
        changePageSize,
        applyFilters,
        clearFilters,
        refreshItems,
    };
}