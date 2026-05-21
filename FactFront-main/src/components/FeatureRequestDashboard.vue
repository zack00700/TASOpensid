<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  RefreshCw,
  Lightbulb,
  Clock,
  CheckCircle,
  PlayCircle,
  AlertCircle,
  TrendingUp,
  MessageSquare,
  List,
  ArrowRight,
} from 'lucide-vue-next';
import { getFeatureRequests } from '../services/featureRequestService';
import type { FeatureRequest, FeatureRequestStatus } from '../types/featureRequest';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────

const requests = ref<FeatureRequest[]>([]);
const isLoading = ref(false);
const error = ref('');

// ── Load ────────────────────────────────────────────────────────────────────

async function load() {
  isLoading.value = true;
  error.value = '';
  try {
    requests.value = await getFeatureRequests({ size: 200 });
  } catch {
    error.value = t('featureRequestDashboard.error.loadFailed');
  } finally {
    isLoading.value = false;
  }
}

onMounted(load);

// ── Stats ───────────────────────────────────────────────────────────────────

const byStatus = computed(() => {
  const map: Record<string, number> = {};
  for (const r of requests.value) {
    map[r.status] = (map[r.status] ?? 0) + 1;
  }
  return map;
});

const total = computed(() => requests.value.length);

const kpis = computed(() => [
  {
    label: t('featureRequestDashboard.kpi.totalRequests'),
    value: total.value,
    icon: Lightbulb,
    color: 'bg-blue-50 text-blue-600 border-blue-100',
    badge: 'bg-blue-100 text-blue-700',
  },
  {
    label: t('featureRequestDashboard.kpi.pendingReview'),
    value: byStatus.value['READY_FOR_REVIEW'] ?? 0,
    icon: AlertCircle,
    color: 'bg-amber-50 text-amber-600 border-amber-100',
    badge: 'bg-amber-100 text-amber-700',
  },
  {
    label: t('featureRequestDashboard.kpi.inProgress'),
    value: byStatus.value['IN_PROGRESS'] ?? 0,
    icon: PlayCircle,
    color: 'bg-indigo-50 text-indigo-600 border-indigo-100',
    badge: 'bg-indigo-100 text-indigo-700',
  },
  {
    label: t('featureRequestDashboard.kpi.completed'),
    value: byStatus.value['DONE'] ?? 0,
    icon: CheckCircle,
    color: 'bg-green-50 text-green-600 border-green-100',
    badge: 'bg-green-100 text-green-700',
  },
]);

// ── Pipeline funnel ──────────────────────────────────────────────────────────

const pipeline = computed(() => [
  { label: t('featureRequestDashboard.pipeline.draft'),           key: 'DRAFT',             color: 'bg-gray-400' },
  { label: t('featureRequestDashboard.pipeline.clarifying'),      key: 'CLARIFYING',        color: 'bg-yellow-400' },
  { label: t('featureRequestDashboard.pipeline.readyForReview'),  key: 'READY_FOR_REVIEW',  color: 'bg-amber-500' },
  { label: t('featureRequestDashboard.pipeline.approved'),        key: 'APPROVED',           color: 'bg-blue-500' },
  { label: t('featureRequestDashboard.pipeline.inProgress'),      key: 'IN_PROGRESS',        color: 'bg-indigo-500' },
  { label: t('featureRequestDashboard.pipeline.done'),            key: 'DONE',               color: 'bg-green-500' },
  { label: t('featureRequestDashboard.pipeline.rejected'),        key: 'REJECTED',           color: 'bg-red-400' },
]);

const maxPipelineCount = computed(() =>
  Math.max(1, ...pipeline.value.map((p) => byStatus.value[p.key] ?? 0))
);

// ── Distribution bar (% of total) ────────────────────────────────────────────

const distribution = computed(() =>
  pipeline.value.map((p) => ({
    ...p,
    count: byStatus.value[p.key] ?? 0,
    pct: total.value ? Math.round(((byStatus.value[p.key] ?? 0) / total.value) * 100) : 0,
  })).filter((p) => p.count > 0)
);

// ── Effort breakdown ─────────────────────────────────────────────────────────

const effortColors: Record<string, string> = {
  S: 'bg-green-400',
  M: 'bg-blue-400',
  L: 'bg-amber-400',
  XL: 'bg-red-400',
};

const effortBreakdown = computed(() => {
  const map: Record<string, number> = { S: 0, M: 0, L: 0, XL: 0 };
  for (const r of requests.value) {
    if (r.estimatedEffort) map[r.estimatedEffort] = (map[r.estimatedEffort] ?? 0) + 1;
  }
  return Object.entries(map)
    .filter(([, v]) => v > 0)
    .map(([k, v]) => ({ label: k, count: v, color: effortColors[k] }));
});

// ── Recent activity ───────────────────────────────────────────────────────────

const recent = computed(() =>
  [...requests.value]
    .sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime())
    .slice(0, 8)
);

// ── Top priority ──────────────────────────────────────────────────────────────

const topPriority = computed(() =>
  [...requests.value]
    .filter((r) => r.status === 'APPROVED' || r.status === 'IN_PROGRESS')
    .sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0))
    .slice(0, 5)
);

// ── Awaiting clarification ────────────────────────────────────────────────────

const awaitingClarification = computed(() =>
  requests.value.filter((r) => r.status === 'CLARIFYING').slice(0, 5)
);

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(d?: string) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

function lastMessage(r: FeatureRequest): string {
  if (!r.conversation?.length) return r.description ?? '';
  const last = r.conversation[r.conversation.length - 1];
  return last.content.slice(0, 80) + (last.content.length > 80 ? '…' : '');
}

const statusConfig = computed<Record<FeatureRequestStatus, { label: string; cls: string }>>(() => ({
  DRAFT:            { label: t('featureRequestDashboard.status.draft'),           cls: 'bg-gray-100 text-gray-600' },
  CLARIFYING:       { label: t('featureRequestDashboard.status.clarifying'),      cls: 'bg-yellow-100 text-yellow-700' },
  READY_FOR_REVIEW: { label: t('featureRequestDashboard.status.readyForReview'),  cls: 'bg-amber-100 text-amber-700' },
  APPROVED:         { label: t('featureRequestDashboard.status.approved'),        cls: 'bg-blue-100 text-blue-700' },
  REJECTED:         { label: t('featureRequestDashboard.status.rejected'),        cls: 'bg-red-100 text-red-600' },
  IN_PROGRESS:      { label: t('featureRequestDashboard.status.inProgress'),      cls: 'bg-indigo-100 text-indigo-700' },
  DONE:             { label: t('featureRequestDashboard.status.done'),            cls: 'bg-green-100 text-green-700' },
}));
</script>

<template>
  <div class="p-6 space-y-6 max-w-7xl mx-auto">

    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">{{ t('featureRequestDashboard.header.title') }}</h1>
        <p class="text-sm text-slate-500 mt-0.5">{{ t('featureRequestDashboard.header.subtitle') }}</p>
      </div>
      <button
        @click="load"
        :disabled="isLoading"
        class="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-medium transition-colors disabled:opacity-50"
      >
        <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': isLoading }" />
        {{ t('featureRequestDashboard.button.refresh') }}
      </button>
    </div>

    <!-- Error -->
    <div v-if="error" class="p-4 rounded-lg bg-red-50 text-red-700 text-sm">{{ error }}</div>

    <!-- KPI cards -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div
        v-for="kpi in kpis"
        :key="kpi.label"
        class="rounded-xl border p-5 flex items-center gap-4"
        :class="kpi.color"
      >
        <div class="w-11 h-11 rounded-full flex items-center justify-center flex-shrink-0" :class="kpi.badge">
          <component :is="kpi.icon" class="w-5 h-5" />
        </div>
        <div>
          <p class="text-3xl font-bold leading-none">{{ kpi.value }}</p>
          <p class="text-xs mt-1 opacity-80 leading-tight">{{ kpi.label }}</p>
        </div>
      </div>
    </div>

    <!-- Pipeline + Distribution row -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

      <!-- Pipeline funnel -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <h2 class="text-sm font-semibold text-slate-700 mb-4 flex items-center gap-2">
          <TrendingUp class="w-4 h-4 text-slate-400" /> {{ t('featureRequestDashboard.section.pipeline') }}
        </h2>
        <div class="space-y-2.5">
          <div v-for="stage in pipeline" :key="stage.key" class="flex items-center gap-3">
            <span class="text-xs text-slate-500 w-36 shrink-0 truncate">{{ stage.label }}</span>
            <div class="flex-1 bg-slate-100 rounded-full h-5 overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-500 flex items-center justify-end pr-2"
                :class="stage.color"
                :style="{ width: maxPipelineCount > 0 ? `${Math.max(4, ((byStatus[stage.key] ?? 0) / maxPipelineCount) * 100)}%` : '4%' }"
              >
              </div>
            </div>
            <span class="text-sm font-bold text-slate-700 w-6 text-right shrink-0">
              {{ byStatus[stage.key] ?? 0 }}
            </span>
          </div>
        </div>
      </div>

      <!-- Distribution stacked bar + effort -->
      <div class="bg-white rounded-xl border border-slate-200 p-5 flex flex-col gap-5">

        <!-- Status distribution -->
        <div>
          <h2 class="text-sm font-semibold text-slate-700 mb-3 flex items-center gap-2">
            <List class="w-4 h-4 text-slate-400" /> {{ t('featureRequestDashboard.section.distribution') }}
          </h2>
          <div v-if="distribution.length" class="flex h-6 rounded-full overflow-hidden gap-0.5">
            <div
              v-for="d in distribution"
              :key="d.key"
              class="h-full transition-all duration-500"
              :class="d.color"
              :style="{ width: d.pct + '%' }"
              :title="t('featureRequestDashboard.distribution.tooltip', { label: d.label, count: d.count, pct: d.pct })"
            />
          </div>
          <div v-else class="h-6 bg-slate-100 rounded-full" />
          <div class="flex flex-wrap gap-x-4 gap-y-1 mt-2">
            <div v-for="d in distribution" :key="d.key" class="flex items-center gap-1.5 text-xs text-slate-600">
              <span class="w-2.5 h-2.5 rounded-full inline-block" :class="d.color" />
              {{ d.label }} ({{ d.count }})
            </div>
          </div>
        </div>

        <!-- Effort breakdown -->
        <div>
          <h2 class="text-sm font-semibold text-slate-700 mb-3 flex items-center gap-2">
            <Clock class="w-4 h-4 text-slate-400" /> {{ t('featureRequestDashboard.section.effort') }}
          </h2>
          <div v-if="effortBreakdown.length" class="flex gap-3">
            <div
              v-for="e in effortBreakdown"
              :key="e.label"
              class="flex-1 rounded-lg p-3 text-center text-white"
              :class="e.color"
            >
              <p class="text-2xl font-bold">{{ e.count }}</p>
              <p class="text-xs font-semibold opacity-90 mt-0.5">{{ e.label }}</p>
            </div>
          </div>
          <p v-else class="text-xs text-slate-400">{{ t('featureRequestDashboard.empty.noEstimates') }}</p>
        </div>
      </div>
    </div>

    <!-- Bottom row: Recent activity + Top priority + Awaiting clarification -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

      <!-- Recent activity -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <h2 class="text-sm font-semibold text-slate-700 mb-4 flex items-center gap-2">
          <MessageSquare class="w-4 h-4 text-slate-400" /> {{ t('featureRequestDashboard.section.recentActivity') }}
        </h2>
        <div v-if="isLoading" class="space-y-3">
          <div v-for="i in 4" :key="i" class="animate-pulse h-10 bg-slate-100 rounded-lg" />
        </div>
        <ul v-else class="space-y-3">
          <li v-for="r in recent" :key="r.id" class="flex gap-3 items-start">
            <span
              class="mt-0.5 inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold shrink-0"
              :class="statusConfig[r.status]?.cls"
            >{{ statusConfig[r.status]?.label }}</span>
            <div class="min-w-0">
              <div class="flex items-center gap-1.5 flex-wrap">
                <span
                  v-if="r.ticketNumber"
                  class="font-mono text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200 px-1.5 py-0.5 rounded-full shrink-0"
                >{{ r.ticketNumber }}</span>
                <p class="text-xs font-medium text-slate-700 truncate">{{ r.title }}</p>
              </div>
              <p class="text-[11px] text-slate-400">{{ formatDate(r.createdAt) }} · {{ r.createdBy }}</p>
            </div>
          </li>
          <li v-if="!recent.length" class="text-xs text-slate-400">{{ t('featureRequestDashboard.empty.noRequests') }}</li>
        </ul>
      </div>

      <!-- Top priority approved/in-progress -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <h2 class="text-sm font-semibold text-slate-700 mb-4 flex items-center gap-2">
          <ArrowRight class="w-4 h-4 text-slate-400" /> {{ t('featureRequestDashboard.section.topPriority') }}
        </h2>
        <ul class="space-y-3">
          <li v-for="r in topPriority" :key="r.id" class="space-y-1">
            <div class="flex items-center justify-between gap-1">
              <div class="flex items-center gap-1.5 min-w-0 flex-1 mr-2">
                <span
                  v-if="r.ticketNumber"
                  class="font-mono text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200 px-1.5 py-0.5 rounded-full shrink-0"
                >{{ r.ticketNumber }}</span>
                <p class="text-xs font-medium text-slate-700 truncate">{{ r.title }}</p>
              </div>
              <span class="text-xs font-bold text-slate-600 shrink-0">{{ r.priority ?? 50 }}</span>
            </div>
            <div class="h-1.5 bg-slate-100 rounded-full overflow-hidden">
              <div
                class="h-full bg-indigo-500 rounded-full transition-all"
                :style="{ width: (r.priority ?? 50) + '%' }"
              />
            </div>
            <span
              class="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold"
              :class="statusConfig[r.status]?.cls"
            >{{ statusConfig[r.status]?.label }}</span>
          </li>
          <li v-if="!topPriority.length" class="text-xs text-slate-400">{{ t('featureRequestDashboard.empty.noApprovedItems') }}</li>
        </ul>
      </div>

      <!-- Awaiting clarification -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <h2 class="text-sm font-semibold text-slate-700 mb-4 flex items-center gap-2">
          <AlertCircle class="w-4 h-4 text-amber-400" /> {{ t('featureRequestDashboard.section.awaitingResponse') }}
        </h2>
        <ul class="space-y-3">
          <li v-for="r in awaitingClarification" :key="r.id" class="space-y-0.5">
            <div class="flex items-center gap-1.5 flex-wrap">
              <span
                v-if="r.ticketNumber"
                class="font-mono text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200 px-1.5 py-0.5 rounded-full shrink-0"
              >{{ r.ticketNumber }}</span>
              <p class="text-xs font-medium text-slate-700 truncate">{{ r.title }}</p>
            </div>
            <p class="text-[11px] text-slate-400 line-clamp-2">{{ lastMessage(r) }}</p>
            <p class="text-[10px] text-amber-500 font-medium">{{ r.createdBy }}</p>
          </li>
          <li v-if="!awaitingClarification.length" class="text-xs text-slate-400">
            {{ t('featureRequestDashboard.empty.noClarification') }}
          </li>
        </ul>
      </div>

    </div>
  </div>
</template>
