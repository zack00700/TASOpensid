<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  Bot, RefreshCw, Eye, CheckCircle, XCircle, PlayCircle, Flag, X,
  MessageSquare, Clock, User, Tag, Calendar,
} from 'lucide-vue-next';
import {
  getBacklog,
  updateStatus,
  addComment,
  assignRequest,
  getMilestones,
} from '../services/featureRequestService';
import FeatureRequestSummaryCard from './FeatureRequestSummaryCard.vue';
import PageHeader from './ui/PageHeader.vue';
import Button from './ui/Button.vue';
import type {
  FeatureRequest,
  FeatureRequestStatus,
  TicketCategory,
} from '../types/featureRequest';

// ── State ──────────────────────────────────────────────────────────────────

const allRequests = ref<FeatureRequest[]>([]);
const isLoading = ref(false);
const errorMessage = ref('');
const availableMilestones = ref<string[]>([]);

// Filters
const activeTab = ref<FeatureRequestStatus | 'ALL'>('ALL');
const filterCategory = ref<TicketCategory | ''>('');
const filterMilestone = ref('');
const filterAssignee = ref('');

const tabs: { label: string; value: FeatureRequestStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Ready for Review', value: 'READY_FOR_REVIEW' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Rejected', value: 'REJECTED' },
];

const categoryOptions: { label: string; value: TicketCategory }[] = [
  { label: 'UI/UX', value: 'UI_UX' },
  { label: 'Billing', value: 'BILLING' },
  { label: 'EDI', value: 'EDI' },
  { label: 'Reporting', value: 'REPORTING' },
  { label: 'Performance', value: 'PERFORMANCE' },
  { label: 'Integration', value: 'INTEGRATION' },
  { label: 'Operations', value: 'OPERATIONS' },
  { label: 'Compliance', value: 'COMPLIANCE' },
  { label: 'Other', value: 'OTHER' },
];

// Detail modal
const showDetailModal = ref(false);
const selectedRequest = ref<FeatureRequest | null>(null);

// Admin action state
const actionPriority = ref(0);
const actionEffort = ref<'S' | 'M' | 'L' | 'XL'>('M');
const actionTags = ref('');
const actionAssignedTo = ref('');
const actionMilestone = ref('');
const actionDueDate = ref('');
const actionCategory = ref<TicketCategory | ''>('');
const rejectReason = ref('');
const showRejectInput = ref(false);
const isActioning = ref(false);
const actionError = ref('');

// Internal comments
const newComment = ref('');
const isAddingComment = ref(false);

// Assign
const isAssigning = ref(false);

// ── Computed ────────────────────────────────────────────────────────────────

const filteredRequests = computed(() => {
  let list = allRequests.value;
  if (activeTab.value !== 'ALL') {
    list = list.filter((r) => r.status === activeTab.value);
  }
  if (filterCategory.value) {
    list = list.filter((r) => r.category === filterCategory.value);
  }
  if (filterMilestone.value.trim()) {
    list = list.filter((r) =>
      r.milestone?.toLowerCase().includes(filterMilestone.value.trim().toLowerCase())
    );
  }
  if (filterAssignee.value.trim()) {
    list = list.filter((r) =>
      r.assignedTo?.toLowerCase().includes(filterAssignee.value.trim().toLowerCase())
    );
  }
  return list;
});

const stats = computed(() => ({
  total: allRequests.value.length,
  readyForReview: allRequests.value.filter((r) => r.status === 'READY_FOR_REVIEW').length,
  approved: allRequests.value.filter((r) => r.status === 'APPROVED').length,
  inProgress: allRequests.value.filter((r) => r.status === 'IN_PROGRESS').length,
}));

// ── Data fetching ───────────────────────────────────────────────────────────

const fetchBacklog = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    allRequests.value = await getBacklog();
  } catch {
    errorMessage.value = 'Failed to load backlog. Please try again.';
  } finally {
    isLoading.value = false;
  }
};

const fetchMilestones = async () => {
  try {
    availableMilestones.value = await getMilestones();
  } catch {
    // silently ignore
  }
};

onMounted(() => {
  fetchBacklog();
  fetchMilestones();
});

// ── Helpers ─────────────────────────────────────────────────────────────────

const getStatusBadgeClasses = (status: string) => {
  const base = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border';
  switch (status) {
    case 'DRAFT':           return `${base} bg-gray-100 text-gray-700 border-gray-200`;
    case 'CLARIFYING':      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'READY_FOR_REVIEW':return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'APPROVED':        return `${base} bg-blue-100 text-blue-800 border-blue-200`;
    case 'REJECTED':        return `${base} bg-red-100 text-red-800 border-red-200`;
    case 'IN_PROGRESS':     return `${base} bg-indigo-100 text-indigo-800 border-indigo-200`;
    case 'DONE':            return `${base} bg-emerald-100 text-emerald-800 border-emerald-200`;
    default:                return `${base} bg-gray-100 text-gray-600 border-gray-200`;
  }
};

const getStatusDotClass = (status?: string) => {
  switch (status) {
    case 'DRAFT':            return 'bg-gray-400';
    case 'CLARIFYING':       return 'bg-yellow-400';
    case 'READY_FOR_REVIEW': return 'bg-green-500';
    case 'APPROVED':         return 'bg-blue-500';
    case 'REJECTED':         return 'bg-red-500';
    case 'IN_PROGRESS':      return 'bg-indigo-500';
    case 'DONE':             return 'bg-emerald-500';
    default:                 return 'bg-gray-300';
  }
};

const getEffortBadgeClasses = (effort?: string) => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded text-xs font-bold border';
  switch (effort) {
    case 'S':  return `${base} bg-emerald-50 text-emerald-700 border-emerald-200`;
    case 'M':  return `${base} bg-blue-50 text-blue-700 border-blue-200`;
    case 'L':  return `${base} bg-orange-50 text-orange-700 border-orange-200`;
    case 'XL': return `${base} bg-red-50 text-red-700 border-red-200`;
    default:   return `${base} bg-gray-50 text-gray-500 border-gray-200`;
  }
};

const getCategoryBadgeClasses = (cat?: string) => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border';
  switch (cat) {
    case 'UI_UX':        return `${base} bg-purple-50 text-purple-700 border-purple-200`;
    case 'BILLING':      return `${base} bg-green-50 text-green-700 border-green-200`;
    case 'EDI':          return `${base} bg-cyan-50 text-cyan-700 border-cyan-200`;
    case 'REPORTING':    return `${base} bg-amber-50 text-amber-700 border-amber-200`;
    case 'PERFORMANCE':  return `${base} bg-rose-50 text-rose-700 border-rose-200`;
    case 'INTEGRATION':  return `${base} bg-indigo-50 text-indigo-700 border-indigo-200`;
    case 'OPERATIONS':   return `${base} bg-teal-50 text-teal-700 border-teal-200`;
    case 'COMPLIANCE':   return `${base} bg-orange-50 text-orange-700 border-orange-200`;
    case 'OTHER':        return `${base} bg-gray-50 text-gray-600 border-gray-200`;
    default:             return `${base} bg-gray-50 text-gray-400 border-gray-100`;
  }
};

const getCategoryLabel = (cat?: string) => {
  const map: Record<string, string> = {
    UI_UX: 'UI/UX', BILLING: 'Billing', EDI: 'EDI', REPORTING: 'Reporting',
    PERFORMANCE: 'Performance', INTEGRATION: 'Integration',
    OPERATIONS: 'Operations', COMPLIANCE: 'Compliance', OTHER: 'Other',
  };
  return cat ? (map[cat] ?? cat) : '—';
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
    });
  } catch {
    return dateStr;
  }
};

const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return dateStr;
  }
};

const getDueDateClasses = (dueDate?: string) => {
  if (!dueDate) return 'text-gray-400';
  const due = new Date(dueDate).getTime();
  const now = Date.now();
  const diff = due - now;
  if (diff < 0) return 'text-red-600 font-semibold';
  if (diff < 7 * 24 * 60 * 60 * 1000) return 'text-yellow-600 font-semibold';
  return 'text-gray-600';
};

const getInitials = (name?: string) => {
  if (!name) return '';
  return name.split(/\s+/).map((w) => w[0]?.toUpperCase() ?? '').slice(0, 2).join('');
};

// ── Detail modal ────────────────────────────────────────────────────────────

const openDetail = (req: FeatureRequest) => {
  selectedRequest.value = { ...req };
  actionPriority.value = req.priority ?? 0;
  actionEffort.value = req.estimatedEffort ?? 'M';
  actionTags.value = req.tags?.join(', ') ?? '';
  actionAssignedTo.value = req.assignedTo ?? '';
  actionMilestone.value = req.milestone ?? '';
  actionDueDate.value = req.dueDate ?? '';
  actionCategory.value = req.category ?? '';
  rejectReason.value = '';
  showRejectInput.value = false;
  actionError.value = '';
  newComment.value = '';
  showDetailModal.value = true;
};

const closeDetail = () => {
  showDetailModal.value = false;
  selectedRequest.value = null;
  actionError.value = '';
};

// ── Admin actions ───────────────────────────────────────────────────────────

const performAction = async (status: FeatureRequestStatus) => {
  if (!selectedRequest.value?.id) return;

  if (status === 'REJECTED' && !rejectReason.value.trim()) {
    showRejectInput.value = true;
    return;
  }

  isActioning.value = true;
  actionError.value = '';
  try {
    const updated = await updateStatus(selectedRequest.value.id, {
      status,
      reason: status === 'REJECTED' ? rejectReason.value.trim() : undefined,
      priority: actionPriority.value,
      estimatedEffort: actionEffort.value,
      assignedTo: actionAssignedTo.value.trim() || undefined,
      milestone: actionMilestone.value.trim() || undefined,
      dueDate: actionDueDate.value || undefined,
    });
    const idx = allRequests.value.findIndex((r) => r.id === updated.id);
    if (idx !== -1) allRequests.value[idx] = updated;
    else allRequests.value.unshift(updated);
    selectedRequest.value = updated;
    showRejectInput.value = false;
  } catch {
    actionError.value = 'Action failed. Please try again.';
  } finally {
    isActioning.value = false;
  }
};

const handleAssign = async () => {
  if (!selectedRequest.value?.id || !actionAssignedTo.value.trim()) return;
  isAssigning.value = true;
  actionError.value = '';
  try {
    const updated = await assignRequest(selectedRequest.value.id, actionAssignedTo.value.trim());
    const idx = allRequests.value.findIndex((r) => r.id === updated.id);
    if (idx !== -1) allRequests.value[idx] = updated;
    selectedRequest.value = updated;
  } catch {
    actionError.value = 'Failed to assign. Please try again.';
  } finally {
    isAssigning.value = false;
  }
};

const handleAddComment = async () => {
  if (!selectedRequest.value?.id || !newComment.value.trim()) return;
  isAddingComment.value = true;
  actionError.value = '';
  try {
    const updated = await addComment(selectedRequest.value.id, newComment.value.trim());
    const idx = allRequests.value.findIndex((r) => r.id === updated.id);
    if (idx !== -1) allRequests.value[idx] = updated;
    selectedRequest.value = updated;
    newComment.value = '';
  } catch {
    actionError.value = 'Failed to add comment. Please try again.';
  } finally {
    isAddingComment.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <PageHeader
      title="Feature Requests — Backlog"
      subtitle="Review, prioritize, and manage incoming feature requests"
      :count="stats.total"
    >
      <template #actions>
        <Button variant="secondary" :disabled="isLoading" @click="fetchBacklog">
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': isLoading }" />
          Refresh
        </Button>
      </template>
    </PageHeader>

    <div class="px-6 py-6 space-y-6">

      <!-- Stats Cards -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <div class="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">Total</p>
          <p class="mt-2 text-3xl font-bold text-gray-900">{{ stats.total }}</p>
        </div>
        <div class="bg-white rounded-xl border border-green-200 shadow-sm p-5">
          <p class="text-xs font-medium text-green-600 uppercase tracking-wide">Ready for Review</p>
          <p class="mt-2 text-3xl font-bold text-green-700">{{ stats.readyForReview }}</p>
        </div>
        <div class="bg-white rounded-xl border border-blue-200 shadow-sm p-5">
          <p class="text-xs font-medium text-blue-600 uppercase tracking-wide">Approved</p>
          <p class="mt-2 text-3xl font-bold text-blue-700">{{ stats.approved }}</p>
        </div>
        <div class="bg-white rounded-xl border border-indigo-200 shadow-sm p-5">
          <p class="text-xs font-medium text-indigo-600 uppercase tracking-wide">In Progress</p>
          <p class="mt-2 text-3xl font-bold text-indigo-700">{{ stats.inProgress }}</p>
        </div>
      </div>

      <!-- Error Banner -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-3">
        <XCircle class="h-5 w-5 text-red-500 flex-shrink-0" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="ml-auto text-red-400 hover:text-red-600">
          <X class="h-4 w-4" />
        </button>
      </div>

      <!-- Table Card -->
      <div class="bg-white shadow-sm rounded-xl border border-gray-200 overflow-hidden">

        <!-- Filter Tabs -->
        <div class="border-b border-gray-200 px-4 overflow-x-auto">
          <nav class="flex gap-1 -mb-px" aria-label="Tabs">
            <button
              v-for="tab in tabs"
              :key="tab.value"
              @click="activeTab = tab.value"
              class="whitespace-nowrap py-4 px-3 text-sm font-medium border-b-2 transition-colors focus:outline-none"
              :class="
                activeTab === tab.value
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              "
            >
              {{ tab.label }}
              <span
                v-if="tab.value !== 'ALL'"
                class="ml-1.5 inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium"
                :class="activeTab === tab.value ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-600'"
              >
                {{ allRequests.filter(r => r.status === tab.value).length }}
              </span>
            </button>
          </nav>
        </div>

        <!-- Secondary Filters -->
        <div class="px-4 py-3 border-b border-gray-100 flex flex-wrap gap-3 bg-gray-50">
          <!-- Category filter -->
          <div class="flex items-center gap-1.5">
            <Tag class="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
            <select
              v-model="filterCategory"
              class="text-sm border border-gray-300 rounded-lg py-1.5 pl-2.5 pr-7 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">All Categories</option>
              <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <!-- Milestone filter -->
          <div class="flex items-center gap-1.5">
            <Flag class="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
            <input
              v-model="filterMilestone"
              type="text"
              placeholder="Filter by milestone..."
              list="milestone-list"
              class="text-sm border border-gray-300 rounded-lg py-1.5 px-2.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 w-40"
            />
            <datalist id="milestone-list">
              <option v-for="m in availableMilestones" :key="m" :value="m" />
            </datalist>
          </div>

          <!-- Assignee filter -->
          <div class="flex items-center gap-1.5">
            <User class="h-3.5 w-3.5 text-gray-400 flex-shrink-0" />
            <input
              v-model="filterAssignee"
              type="text"
              placeholder="Filter by assignee..."
              class="text-sm border border-gray-300 rounded-lg py-1.5 px-2.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 w-40"
            />
          </div>

          <!-- Clear filters -->
          <button
            v-if="filterCategory || filterMilestone || filterAssignee"
            @click="filterCategory = ''; filterMilestone = ''; filterAssignee = ''"
            class="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 px-2 py-1.5 rounded-lg hover:bg-gray-100 transition-colors"
          >
            <X class="h-3 w-3" /> Clear filters
          </button>
        </div>

        <!-- Loading skeleton -->
        <div v-if="isLoading" class="divide-y divide-gray-100">
          <div v-for="i in 5" :key="i" class="px-6 py-4">
            <div class="flex items-center gap-4 animate-pulse">
              <div class="h-4 bg-gray-200 rounded w-24"></div>
              <div class="h-4 bg-gray-200 rounded flex-1"></div>
              <div class="h-4 bg-gray-200 rounded w-20"></div>
              <div class="h-4 bg-gray-200 rounded w-16"></div>
              <div class="h-4 bg-gray-200 rounded w-20"></div>
            </div>
          </div>
        </div>

        <!-- Table -->
        <div v-else-if="filteredRequests.length" class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-100">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Ticket #</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Title</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Category</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Assigned To</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Priority</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Effort</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Milestone</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Due Date</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created At</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider sr-only">Actions</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-100">
              <tr
                v-for="req in filteredRequests"
                :key="req.id"
                class="hover:bg-gray-50 transition-colors cursor-pointer"
                @click="openDetail(req)"
              >
                <!-- Ticket # -->
                <td class="px-4 py-4">
                  <span
                    v-if="req.ticketNumber"
                    class="font-mono text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200 px-2 py-0.5 rounded-full whitespace-nowrap"
                  >
                    {{ req.ticketNumber }}
                  </span>
                  <span v-else class="text-xs text-gray-400 font-mono">—</span>
                </td>

                <!-- Title -->
                <td class="px-4 py-4">
                  <p class="text-sm font-medium text-gray-900 truncate max-w-xs">{{ req.title }}</p>
                  <p v-if="req.tags?.length" class="text-xs text-gray-400 mt-0.5 truncate">
                    {{ req.tags.join(', ') }}
                  </p>
                </td>

                <!-- Category -->
                <td class="px-4 py-4">
                  <span v-if="req.category" :class="getCategoryBadgeClasses(req.category)">
                    {{ getCategoryLabel(req.category) }}
                  </span>
                  <span v-else class="text-xs text-gray-400">—</span>
                </td>

                <!-- Assigned To -->
                <td class="px-4 py-4">
                  <div v-if="req.assignedTo" class="flex items-center gap-2">
                    <div class="w-6 h-6 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-xs font-bold flex-shrink-0">
                      {{ getInitials(req.assignedTo) }}
                    </div>
                    <span class="text-xs text-gray-600 truncate max-w-[80px]">{{ req.assignedTo }}</span>
                  </div>
                  <span v-else class="text-xs text-gray-400">—</span>
                </td>

                <!-- Status -->
                <td class="px-4 py-4">
                  <span :class="getStatusBadgeClasses(req.status)">
                    {{ req.status.replace(/_/g, ' ') }}
                  </span>
                </td>

                <!-- Priority -->
                <td class="px-4 py-4">
                  <div v-if="req.priority !== undefined" class="flex items-center gap-2">
                    <div class="w-16 bg-gray-200 rounded-full h-1.5 overflow-hidden">
                      <div
                        class="h-1.5 rounded-full bg-gradient-to-r from-blue-400 to-indigo-600 transition-all"
                        :style="{ width: `${req.priority}%` }"
                      ></div>
                    </div>
                    <span class="text-xs text-gray-500 tabular-nums">{{ req.priority }}</span>
                  </div>
                  <span v-else class="text-xs text-gray-400">—</span>
                </td>

                <!-- Effort -->
                <td class="px-4 py-4">
                  <span v-if="req.estimatedEffort" :class="getEffortBadgeClasses(req.estimatedEffort)">
                    {{ req.estimatedEffort }}
                  </span>
                  <span v-else class="text-xs text-gray-400">—</span>
                </td>

                <!-- Milestone -->
                <td class="px-4 py-4">
                  <span
                    v-if="req.milestone"
                    class="inline-flex items-center px-2 py-0.5 rounded text-xs bg-gray-100 text-gray-600 border border-gray-200"
                  >
                    {{ req.milestone }}
                  </span>
                  <span v-else class="text-xs text-gray-400">—</span>
                </td>

                <!-- Due Date -->
                <td class="px-4 py-4">
                  <span class="text-xs whitespace-nowrap" :class="getDueDateClasses(req.dueDate)">
                    {{ req.dueDate ? formatDate(req.dueDate) : '—' }}
                  </span>
                </td>

                <!-- Created At -->
                <td class="px-4 py-4">
                  <span class="text-sm text-gray-500 whitespace-nowrap">{{ formatDate(req.createdAt) }}</span>
                </td>

                <!-- Actions -->
                <td class="px-4 py-4" @click.stop>
                  <button
                    @click="openDetail(req)"
                    class="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-200 rounded-lg text-xs font-medium text-gray-600 hover:bg-gray-50 hover:text-blue-600 hover:border-blue-200 transition-colors"
                  >
                    <Eye class="h-3.5 w-3.5" />
                    View
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Empty state -->
        <div v-else class="text-center py-16">
          <Bot class="mx-auto h-12 w-12 text-gray-300 mb-4" />
          <h3 class="text-sm font-medium text-gray-900">No feature requests</h3>
          <p class="mt-1 text-sm text-gray-500">
            {{ activeTab !== 'ALL' ? 'No requests with this status.' : 'No feature requests have been submitted yet.' }}
          </p>
        </div>
      </div>
    </div>

    <!-- ── Detail Modal ──────────────────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="showDetailModal && selectedRequest"
        class="fixed inset-0 bg-gray-900/60 backdrop-blur-sm flex items-center justify-center z-50 p-4"
        @click.self="closeDetail"
      >
        <div class="bg-white rounded-2xl shadow-2xl w-full max-w-5xl max-h-[92vh] flex flex-col overflow-hidden">

          <!-- Modal Header -->
          <div class="flex items-start justify-between p-5 border-b border-gray-100 gap-4 bg-white">
            <div class="flex items-center gap-3 min-w-0">
              <!-- Large ticket number badge -->
              <span
                v-if="selectedRequest.ticketNumber"
                class="font-mono text-sm font-bold bg-blue-50 text-blue-700 border border-blue-200 px-3 py-1 rounded-full whitespace-nowrap flex-shrink-0"
              >
                {{ selectedRequest.ticketNumber }}
              </span>
              <div class="min-w-0">
                <h3 class="text-lg font-semibold text-gray-900 leading-snug truncate">{{ selectedRequest.title }}</h3>
                <div class="flex items-center gap-2 mt-1 flex-wrap">
                  <span :class="getStatusBadgeClasses(selectedRequest.status)">
                    {{ selectedRequest.status.replace(/_/g, ' ') }}
                  </span>
                  <span v-if="selectedRequest.category" :class="getCategoryBadgeClasses(selectedRequest.category)">
                    {{ getCategoryLabel(selectedRequest.category) }}
                  </span>
                  <span v-if="selectedRequest.createdBy" class="text-xs text-gray-400">by {{ selectedRequest.createdBy }}</span>
                  <span v-if="selectedRequest.createdAt" class="text-xs text-gray-400">· {{ formatDate(selectedRequest.createdAt) }}</span>
                </div>
              </div>
            </div>
            <button
              @click="closeDetail"
              class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0"
            >
              <X class="h-5 w-5" />
            </button>
          </div>

          <!-- Modal Body: Two panels -->
          <div class="flex flex-col lg:flex-row flex-1 overflow-hidden min-h-0">

            <!-- ── LEFT PANEL: Ticket details ──────────────────────────── -->
            <div class="flex-1 overflow-y-auto border-b lg:border-b-0 lg:border-r border-gray-100 divide-y divide-gray-100">

              <!-- Description + Summary -->
              <div class="p-5 space-y-4">
                <div>
                  <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">Description</p>
                  <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{{ selectedRequest.description || '—' }}</p>
                </div>

                <div v-if="selectedRequest.structuredSummaryData || selectedRequest.structuredSummary">
                  <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">AI Summary</p>
                  <FeatureRequestSummaryCard
                    v-if="selectedRequest.structuredSummaryData"
                    :data="selectedRequest.structuredSummaryData"
                  />
                  <div
                    v-else
                    class="bg-gradient-to-br from-slate-50 to-blue-50 border border-blue-100 rounded-xl p-4 flex items-start gap-3"
                  >
                    <Bot class="w-4 h-4 text-indigo-500 flex-shrink-0 mt-0.5" />
                    <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{{ selectedRequest.structuredSummary }}</p>
                  </div>
                </div>
              </div>

              <!-- Conversation thread -->
              <div v-if="selectedRequest.conversation?.length" class="p-5 space-y-3">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3 flex items-center gap-1.5">
                  <MessageSquare class="w-3.5 h-3.5" /> Conversation Thread
                </p>
                <div
                  v-for="(msg, i) in selectedRequest.conversation"
                  :key="i"
                  class="flex"
                  :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
                >
                  <div v-if="msg.role === 'assistant'" class="flex items-start gap-2 max-w-[85%]">
                    <div class="w-6 h-6 rounded-full bg-gradient-to-br from-indigo-400 to-blue-500 flex items-center justify-center flex-shrink-0 mt-0.5">
                      <Bot class="w-3 h-3 text-white" />
                    </div>
                    <div class="bg-gradient-to-br from-slate-50 to-blue-50 border border-blue-100 rounded-xl rounded-tl-sm px-3.5 py-2.5">
                      <p class="text-sm text-gray-800 leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                      <p v-if="msg.timestamp" class="text-xs text-gray-400 mt-1">
                        {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                      </p>
                    </div>
                  </div>
                  <div v-else class="max-w-[85%]">
                    <div class="bg-gradient-to-br from-blue-500 to-indigo-600 rounded-xl rounded-tr-sm px-3.5 py-2.5">
                      <p class="text-sm text-white leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                      <p v-if="msg.timestamp" class="text-xs text-blue-200 mt-1 text-right">
                        {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ── RIGHT PANEL: Ticket management ──────────────────────── -->
            <div class="w-full lg:w-80 xl:w-96 flex-shrink-0 overflow-y-auto divide-y divide-gray-100">

              <!-- Action error -->
              <div v-if="actionError" class="mx-4 mt-4 bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                {{ actionError }}
              </div>

              <!-- Section 1: Properties -->
              <div class="p-5 space-y-4">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Properties</p>

                <!-- Category -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">Category</label>
                  <select
                    v-model="actionCategory"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="">— None —</option>
                    <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </option>
                  </select>
                </div>

                <!-- Priority slider -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">
                    Priority
                    <span class="ml-1.5 text-blue-600 font-bold tabular-nums">{{ actionPriority }}</span>
                  </label>
                  <input
                    v-model.number="actionPriority"
                    type="range" min="0" max="100" step="1"
                    class="w-full h-2 rounded-lg appearance-none cursor-pointer bg-gray-200 accent-blue-600"
                  />
                  <div class="flex justify-between text-xs text-gray-400 mt-1">
                    <span>0 – Low</span>
                    <span>100 – Critical</span>
                  </div>
                </div>

                <!-- Effort buttons -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">Estimated Effort</label>
                  <div class="flex gap-2">
                    <button
                      v-for="opt in (['S', 'M', 'L', 'XL'] as const)"
                      :key="opt"
                      @click="actionEffort = opt"
                      class="flex-1 py-1.5 rounded-lg text-sm font-bold border-2 transition-all"
                      :class="
                        actionEffort === opt
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-gray-200 text-gray-500 hover:border-gray-300 hover:bg-gray-50'
                      "
                    >
                      {{ opt }}
                    </button>
                  </div>
                </div>

                <!-- Assigned To -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">Assigned To</label>
                  <div class="flex gap-2">
                    <input
                      v-model="actionAssignedTo"
                      type="text"
                      placeholder="e.g. john.doe"
                      class="flex-1 border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 min-w-0"
                    />
                    <button
                      @click="handleAssign"
                      :disabled="isAssigning || !actionAssignedTo.trim()"
                      class="inline-flex items-center gap-1 px-3 py-2 rounded-lg text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 transition-colors flex-shrink-0"
                    >
                      <User class="w-3.5 h-3.5" />
                      Assign
                    </button>
                  </div>
                </div>

                <!-- Milestone -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">Milestone</label>
                  <input
                    v-model="actionMilestone"
                    type="text"
                    placeholder="e.g. v2.5"
                    list="milestone-datalist"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                  <datalist id="milestone-datalist">
                    <option v-for="m in availableMilestones" :key="m" :value="m" />
                  </datalist>
                </div>

                <!-- Due Date -->
                <div>
                  <label class="block text-xs font-medium text-gray-600 mb-1.5">
                    <span class="flex items-center gap-1"><Calendar class="w-3.5 h-3.5" /> Due Date</span>
                  </label>
                  <input
                    v-model="actionDueDate"
                    type="date"
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
              </div>

              <!-- Section 2: Actions -->
              <div class="p-5 space-y-3">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Actions</p>

                <!-- Reject reason -->
                <div v-if="showRejectInput">
                  <label class="block text-xs font-medium text-red-700 mb-1.5">
                    Reason for rejection <span class="text-red-500">*</span>
                  </label>
                  <textarea
                    v-model="rejectReason"
                    rows="3"
                    placeholder="Explain why this request is being rejected..."
                    class="block w-full border border-red-300 rounded-lg py-2 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-red-500 resize-none"
                  ></textarea>
                </div>

                <div class="flex flex-wrap gap-2">
                  <button
                    @click="performAction('APPROVED')"
                    :disabled="isActioning"
                    class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-green-600 hover:bg-green-700 disabled:opacity-50 transition-colors shadow-sm"
                  >
                    <CheckCircle class="w-3.5 h-3.5" /> Approve
                  </button>
                  <button
                    @click="performAction('IN_PROGRESS')"
                    :disabled="isActioning"
                    class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-sm"
                  >
                    <PlayCircle class="w-3.5 h-3.5" /> In Progress
                  </button>
                  <button
                    @click="performAction('DONE')"
                    :disabled="isActioning"
                    class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-gray-600 hover:bg-gray-700 disabled:opacity-50 transition-colors shadow-sm"
                  >
                    <Flag class="w-3.5 h-3.5" /> Done
                  </button>
                  <button
                    @click="showRejectInput ? performAction('REJECTED') : (showRejectInput = true)"
                    :disabled="isActioning || (showRejectInput && !rejectReason.trim())"
                    class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 transition-colors shadow-sm"
                  >
                    <XCircle class="w-3.5 h-3.5" />
                    {{ showRejectInput ? 'Confirm Reject' : 'Reject' }}
                  </button>
                  <button
                    v-if="showRejectInput"
                    @click="showRejectInput = false; rejectReason = ''"
                    class="inline-flex items-center gap-1 px-3 py-2 rounded-lg text-xs font-medium text-gray-600 bg-gray-100 hover:bg-gray-200 transition-colors"
                  >
                    <X class="w-3.5 h-3.5" /> Cancel
                  </button>
                </div>

                <div v-if="isActioning" class="flex items-center gap-2 text-xs text-gray-500">
                  <RefreshCw class="w-3.5 h-3.5 animate-spin text-blue-500" />
                  Saving changes...
                </div>
              </div>

              <!-- Section 3: Internal Comments -->
              <div class="p-5 space-y-3">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                  <MessageSquare class="w-3.5 h-3.5" /> Internal Comments
                </p>

                <!-- Existing comments -->
                <div
                  v-if="selectedRequest.internalComments?.length"
                  class="space-y-3 max-h-48 overflow-y-auto"
                >
                  <div
                    v-for="comment in selectedRequest.internalComments"
                    :key="comment.commentId ?? comment.createdAt"
                    class="bg-gray-50 rounded-lg p-3 border border-gray-100"
                  >
                    <div class="flex items-center gap-2 mb-1">
                      <div class="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[10px] font-bold flex-shrink-0">
                        {{ getInitials(comment.authorId) || '?' }}
                      </div>
                      <span class="text-xs font-medium text-gray-700">{{ comment.authorId || 'Admin' }}</span>
                      <span class="text-[10px] text-gray-400 ml-auto">{{ formatDate(comment.createdAt) }}</span>
                    </div>
                    <p class="text-xs text-gray-600 leading-relaxed">{{ comment.content }}</p>
                  </div>
                </div>
                <p v-else class="text-xs text-gray-400">No internal comments yet.</p>

                <!-- Add comment -->
                <div class="space-y-2">
                  <textarea
                    v-model="newComment"
                    rows="2"
                    placeholder="Add an internal comment (admin only)..."
                    class="block w-full border border-gray-300 rounded-lg py-2 px-3 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-none"
                  ></textarea>
                  <button
                    @click="handleAddComment"
                    :disabled="isAddingComment || !newComment.trim()"
                    class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 transition-colors"
                  >
                    <MessageSquare class="w-3.5 h-3.5" />
                    {{ isAddingComment ? 'Adding...' : 'Add Comment' }}
                  </button>
                </div>
              </div>

              <!-- Section 4: Audit Trail / Timeline -->
              <div class="p-5 space-y-3">
                <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                  <Clock class="w-3.5 h-3.5" /> Audit Trail
                </p>

                <div
                  v-if="selectedRequest.statusHistory?.length"
                  class="relative pl-4"
                >
                  <!-- vertical line -->
                  <div class="absolute left-[7px] top-2 bottom-2 w-px bg-gray-200" />

                  <div
                    v-for="(entry, idx) in selectedRequest.statusHistory"
                    :key="idx"
                    class="relative flex items-start gap-3 pb-4 last:pb-0"
                  >
                    <!-- Color dot -->
                    <div
                      class="w-3.5 h-3.5 rounded-full border-2 border-white flex-shrink-0 mt-0.5 z-10"
                      :class="getStatusDotClass(entry.toStatus)"
                    />
                    <div class="flex-1 min-w-0">
                      <p class="text-xs text-gray-700 leading-snug">
                        <span v-if="entry.fromStatus" class="text-gray-500">
                          <span class="font-medium text-gray-700">{{ entry.fromStatus.replace(/_/g, ' ') }}</span>
                          →
                        </span>
                        <span class="font-semibold text-gray-900">{{ entry.toStatus.replace(/_/g, ' ') }}</span>
                        <span v-if="entry.changedBy" class="text-gray-500"> by {{ entry.changedBy }}</span>
                      </p>
                      <p v-if="entry.changedAt" class="text-[10px] text-gray-400 mt-0.5">{{ formatDateTime(entry.changedAt) }}</p>
                      <p v-if="entry.reason" class="text-[11px] text-gray-500 mt-0.5 italic">{{ entry.reason }}</p>
                    </div>
                  </div>
                </div>
                <p v-else class="text-xs text-gray-400">No status history yet.</p>
              </div>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end">
            <button
              @click="closeDetail"
              class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
