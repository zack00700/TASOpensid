<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-vue-next';

const { t } = useI18n();

interface PaginationMetadata {
  currentPage: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

const props = defineProps<{
  pagination: PaginationMetadata;
  maxVisiblePages?: number;
}>();

const emit = defineEmits<{
  (e: 'page-change', page: number): void;
  (e: 'size-change', size: number): void;
}>();

const maxVisible = props.maxVisiblePages || 7;

// Calculate visible page numbers
const visiblePages = computed(() => {
  const total = props.pagination.totalPages;
  const current = props.pagination.currentPage;
  
  if (total <= maxVisible) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  
  const halfVisible = Math.floor(maxVisible / 2);
  let start = Math.max(1, current - halfVisible);
  let end = Math.min(total, start + maxVisible - 1);
  
  // Adjust start if we're near the end
  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1);
  }
  
  return Array.from({ length: end - start + 1 }, (_, i) => start + i);
});

// Page size options
const pageSizeOptions = [10, 20, 50, 100];

const handlePageChange = (page: number) => {
  if (page >= 1 && page <= props.pagination.totalPages && page !== props.pagination.currentPage) {
    emit('page-change', page);
  }
};

const handleSizeChange = (event: Event) => {
  const target = event.target as HTMLSelectElement;
  const newSize = parseInt(target.value);
  if (newSize !== props.pagination.pageSize) {
    emit('size-change', newSize);
  }
};

// Calculate showing range
const showingStart = computed(() => {
  return Math.min(
    (props.pagination.currentPage - 1) * props.pagination.pageSize + 1,
    props.pagination.totalItems
  );
});

const showingEnd = computed(() => {
  return Math.min(
    props.pagination.currentPage * props.pagination.pageSize,
    props.pagination.totalItems
  );
});
</script>

<template>
  <div class="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 sm:px-6">
    <!-- Mobile pagination -->
    <div class="flex flex-1 justify-between sm:hidden">
      <button
        @click="handlePageChange(pagination.currentPage - 1)"
        :disabled="!pagination.hasPrevious"
        class="relative inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {{ t('pagination.previous') }}
      </button>
      <button
        @click="handlePageChange(pagination.currentPage + 1)"
        :disabled="!pagination.hasNext"
        class="relative ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {{ t('pagination.next') }}
      </button>
    </div>

    <!-- Desktop pagination -->
    <div class="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
      <!-- Results info and page size selector -->
      <div class="flex items-center space-x-4">
        <p class="text-sm text-gray-700">
          {{ t('pagination.showing') }}
          <span class="font-medium">{{ showingStart }}</span>
          {{ t('pagination.to') }}
          <span class="font-medium">{{ showingEnd }}</span>
          {{ t('pagination.of') }}
          <span class="font-medium">{{ pagination.totalItems }}</span>
          {{ t('pagination.results') }}
        </p>

        <div class="flex items-center space-x-2">
          <label for="page-size" class="text-sm text-gray-700">{{ t('pagination.show') }}</label>
          <select
            id="page-size"
            :value="pagination.pageSize"
            @change="handleSizeChange"
            class="rounded-md border border-gray-300 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
          >
            <option v-for="size in pageSizeOptions" :key="size" :value="size">
              {{ size }}
            </option>
          </select>
        </div>
      </div>

      <!-- Page navigation -->
      <div class="flex items-center space-x-1">
        <!-- First page -->
        <button
          @click="handlePageChange(1)"
          :disabled="pagination.currentPage === 1"
          class="relative inline-flex items-center rounded-l-md px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          :title="t('pagination.label.firstPage')"
        >
          <ChevronsLeft class="h-5 w-5" />
        </button>

        <!-- Previous page -->
        <button
          @click="handlePageChange(pagination.currentPage - 1)"
          :disabled="!pagination.hasPrevious"
          class="relative inline-flex items-center px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          :title="t('pagination.label.previousPage')"
        >
          <ChevronLeft class="h-5 w-5" />
        </button>

        <!-- Page numbers -->
        <div class="flex">
          <!-- Show ellipsis before if needed -->
          <span
            v-if="visiblePages[0] > 1"
            class="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-gray-700 ring-1 ring-inset ring-gray-300"
          >
            ...
          </span>

          <!-- Page number buttons -->
          <button
            v-for="page in visiblePages"
            :key="page"
            @click="handlePageChange(page)"
            :class="[
              'relative inline-flex items-center px-4 py-2 text-sm font-semibold ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0',
              page === pagination.currentPage
                ? 'z-10 bg-blue-600 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600'
                : 'text-gray-900'
            ]"
          >
            {{ page }}
          </button>

          <!-- Show ellipsis after if needed -->
          <span
            v-if="visiblePages[visiblePages.length - 1] < pagination.totalPages"
            class="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-gray-700 ring-1 ring-inset ring-gray-300"
          >
            ...
          </span>
        </div>

        <!-- Next page -->
        <button
          @click="handlePageChange(pagination.currentPage + 1)"
          :disabled="!pagination.hasNext"
          class="relative inline-flex items-center px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          :title="t('pagination.label.nextPage')"
        >
          <ChevronRight class="h-5 w-5" />
        </button>

        <!-- Last page -->
        <button
          @click="handlePageChange(pagination.totalPages)"
          :disabled="pagination.currentPage === pagination.totalPages"
          class="relative inline-flex items-center rounded-r-md px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          :title="t('pagination.label.lastPage')"
        >
          <ChevronsRight class="h-5 w-5" />
        </button>
      </div>
    </div>
  </div>
</template>