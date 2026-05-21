<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Line } from 'vue-chartjs';

const { t } = useI18n();
import {
  Chart,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { RefreshCw, TrendingUp, AlertCircle } from 'lucide-vue-next';
import { getThroughputForecast, listForecastMetrics } from '../services/forecastingService';
import type { CapacityForecast } from '../types/forecasting';

Chart.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Title,
  Tooltip,
  Legend
);

// ── State ────────────────────────────────────────────────────────────────────

const metrics = ref<string[]>([]);
const selectedMetric = ref<string>('YARD_MOVES');
const lookbackMonths = ref<number>(12);
const horizonMonths = ref<number>(6);

const forecast = ref<CapacityForecast | null>(null);
const isLoading = ref(false);
const errorMessage = ref('');

// ── Data fetching ─────────────────────────────────────────────────────────────

const fetchMetrics = async () => {
  try {
    metrics.value = await listForecastMetrics();
    if (metrics.value.length && !metrics.value.includes(selectedMetric.value)) {
      selectedMetric.value = metrics.value[0];
    }
  } catch {
    // Fall back to hard-coded list matching the backend enum.
    metrics.value = ['YARD_MOVES', 'GATE_IN', 'GATE_OUT', 'VESSEL_CALLS', 'SHIFTS_STARTED'];
  }
};

const fetchForecast = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    forecast.value = await getThroughputForecast({
      metric: selectedMetric.value,
      lookbackMonths: lookbackMonths.value,
      horizonMonths: horizonMonths.value,
    });
  } catch {
    errorMessage.value = t('capacityForecastDashboard.error.loadFailed');
    forecast.value = null;
  } finally {
    isLoading.value = false;
  }
};

onMounted(async () => {
  await fetchMetrics();
  await fetchForecast();
});

watch([selectedMetric, lookbackMonths, horizonMonths], () => {
  fetchForecast();
});

// ── Chart data ────────────────────────────────────────────────────────────────

const chartData = computed(() => {
  if (!forecast.value) {
    return { labels: [], datasets: [] };
  }
  const f = forecast.value;
  const labels = [...f.historicalYearMonths, ...f.forecastYearMonths];
  const historicalLen = f.historicalValues.length;

  // The forecast line starts at the last historical point so the two segments
  // visually connect — otherwise there's a gap between the end of history and
  // the first forecast point.
  const joinPoint = historicalLen > 0 ? f.historicalValues[historicalLen - 1] : null;
  const forecastLine: (number | null)[] = [
    ...Array(historicalLen - 1).fill(null),
    joinPoint,
    ...f.forecastValues,
  ];
  const historicalLine: (number | null)[] = [
    ...f.historicalValues,
    ...Array(f.forecastValues.length).fill(null),
  ];
  const upperBand: (number | null)[] = [
    ...Array(historicalLen).fill(null),
    ...f.forecastUpperBound,
  ];
  const lowerBand: (number | null)[] = [
    ...Array(historicalLen).fill(null),
    ...f.forecastLowerBound,
  ];

  return {
    labels,
    datasets: [
      {
        label: t('capacityForecastDashboard.dataset.upperBand'),
        data: upperBand,
        borderColor: 'transparent',
        backgroundColor: 'rgba(59, 130, 246, 0.12)',
        pointRadius: 0,
        fill: '+1',
        tension: 0.25,
      },
      {
        label: t('capacityForecastDashboard.dataset.lowerBand'),
        data: lowerBand,
        borderColor: 'transparent',
        backgroundColor: 'rgba(59, 130, 246, 0.12)',
        pointRadius: 0,
        fill: false,
        tension: 0.25,
      },
      {
        label: t('capacityForecastDashboard.dataset.historical'),
        data: historicalLine,
        borderColor: '#2563eb',
        backgroundColor: '#2563eb',
        borderWidth: 2,
        pointRadius: 3,
        tension: 0.25,
        spanGaps: false,
      },
      {
        label: t('capacityForecastDashboard.dataset.forecast'),
        data: forecastLine,
        borderColor: '#2563eb',
        backgroundColor: '#2563eb',
        borderWidth: 2,
        borderDash: [6, 4],
        pointRadius: 3,
        tension: 0.25,
        spanGaps: true,
      },
    ],
  };
});

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { intersect: false, mode: 'index' as const },
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        filter: (item: any) => item.text !== t('capacityForecastDashboard.dataset.lowerBand'),
      },
    },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          if (ctx.parsed.y == null) return '';
          const value = Number(ctx.parsed.y).toFixed(0);
          return `${ctx.dataset.label}: ${value}`;
        },
      },
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      ticks: { precision: 0 },
    },
  },
}));

// ── Derived stats ─────────────────────────────────────────────────────────────

const latestHistorical = computed<number | null>(() => {
  const hist = forecast.value?.historicalValues;
  return hist && hist.length ? hist[hist.length - 1] : null;
});

const nextMonthForecast = computed<number | null>(() => {
  const fc = forecast.value?.forecastValues;
  return fc && fc.length ? fc[0] : null;
});

const slopeLabel = computed(() => {
  const s = forecast.value?.slopePerMonth;
  if (s == null) return '—';
  const sign = s >= 0 ? '+' : '';
  return t('capacityForecastDashboard.stats.slopePerMonth', { value: `${sign}${s.toFixed(1)}` });
});

const r2Label = computed(() => {
  const r2 = forecast.value?.r2;
  return r2 == null ? '—' : r2.toFixed(2);
});

const r2Quality = computed(() => {
  const r2 = forecast.value?.r2 ?? 0;
  if (r2 >= 0.7) return { text: t('capacityForecastDashboard.r2Quality.good'), classes: 'text-green-700 bg-green-50 border-green-200' };
  if (r2 >= 0.4) return { text: t('capacityForecastDashboard.r2Quality.medium'), classes: 'text-yellow-700 bg-yellow-50 border-yellow-200' };
  return { text: t('capacityForecastDashboard.r2Quality.noisy'), classes: 'text-red-700 bg-red-50 border-red-200' };
});

const metricLabel = (m: string) => {
  switch (m) {
    case 'YARD_MOVES': return t('capacityForecastDashboard.metric.yardMoves');
    case 'GATE_IN': return t('capacityForecastDashboard.metric.gateIn');
    case 'GATE_OUT': return t('capacityForecastDashboard.metric.gateOut');
    case 'VESSEL_CALLS': return t('capacityForecastDashboard.metric.vesselCalls');
    case 'SHIFTS_STARTED': return t('capacityForecastDashboard.metric.shiftsStarted');
    default: return m;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Page header -->
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl">
            {{ t('capacityForecastDashboard.header.title') }}
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            {{ t('capacityForecastDashboard.header.subtitle', { months: lookbackMonths }) }}
          </p>
        </div>
        <button
          @click="fetchForecast"
          :disabled="isLoading"
          class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
        >
          <RefreshCw class="h-4 w-4 mr-2" :class="{ 'animate-spin': isLoading }" />
          {{ t('capacityForecastDashboard.action.refresh') }}
        </button>
      </div>

      <!-- Controls -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('capacityForecastDashboard.field.metric') }}</label>
            <select
              v-model="selectedMetric"
              class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            >
              <option v-for="m in metrics" :key="m" :value="m">{{ metricLabel(m) }}</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700 mb-1">
              {{ t('capacityForecastDashboard.field.lookbackMonths') }} <span class="text-gray-400">{{ t('capacityForecastDashboard.field.lookbackMonthsHint') }}</span>
            </label>
            <input
              v-model.number="lookbackMonths"
              type="number"
              min="2"
              max="36"
              class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700 mb-1">{{ t('capacityForecastDashboard.field.horizonMonths') }}</label>
            <input
              v-model.number="horizonMonths"
              type="number"
              min="1"
              max="24"
              class="block w-full border border-gray-300 rounded-lg py-2.5 px-3 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
        </div>
      </div>

      <!-- Error banner -->
      <div v-if="errorMessage" class="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center space-x-3">
        <AlertCircle class="h-5 w-5 text-red-500 flex-shrink-0" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
      </div>

      <!-- Stats row -->
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('capacityForecastDashboard.stats.currentMonth') }}</p>
          <p class="mt-2 text-2xl font-bold text-gray-900">
            <span v-if="isLoading" class="h-6 w-16 bg-gray-200 rounded animate-pulse inline-block"></span>
            <span v-else>{{ latestHistorical ?? '—' }}</span>
          </p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('capacityForecastDashboard.stats.nextMonthForecast') }}</p>
          <p class="mt-2 text-2xl font-bold text-blue-600">
            <span v-if="isLoading" class="h-6 w-16 bg-gray-200 rounded animate-pulse inline-block"></span>
            <span v-else>{{ nextMonthForecast != null ? Math.round(nextMonthForecast) : '—' }}</span>
          </p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('capacityForecastDashboard.stats.trend') }}</p>
          <p class="mt-2 text-2xl font-bold text-gray-900 flex items-center">
            <TrendingUp v-if="(forecast?.slopePerMonth ?? 0) >= 0" class="h-5 w-5 text-green-500 mr-2" />
            <TrendingUp v-else class="h-5 w-5 text-red-500 mr-2 rotate-180" />
            <span v-if="isLoading" class="h-6 w-20 bg-gray-200 rounded animate-pulse inline-block"></span>
            <span v-else>{{ slopeLabel }}</span>
          </p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
          <p class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ t('capacityForecastDashboard.stats.r2Fit') }}</p>
          <p class="mt-2 text-2xl font-bold text-gray-900">
            <span v-if="isLoading" class="h-6 w-12 bg-gray-200 rounded animate-pulse inline-block"></span>
            <span v-else>{{ r2Label }}</span>
          </p>
          <span
            v-if="forecast"
            class="mt-1 inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border"
            :class="r2Quality.classes"
          >{{ r2Quality.text }}</span>
        </div>
      </div>

      <!-- Chart card -->
      <div class="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div class="h-96">
          <div v-if="isLoading" class="h-full flex items-center justify-center">
            <div class="flex items-center space-x-3 text-gray-400">
              <RefreshCw class="h-5 w-5 animate-spin" />
              <span class="text-sm">{{ t('capacityForecastDashboard.state.computing') }}</span>
            </div>
          </div>
          <Line
            v-else-if="forecast && forecast.historicalValues.length"
            :data="chartData"
            :options="chartOptions"
          />
          <div v-else class="h-full flex items-center justify-center text-sm text-gray-500">
            {{ t('capacityForecastDashboard.state.noData') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
