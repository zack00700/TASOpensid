<template>
  <div class="min-h-screen bg-gray-50 p-6">
    <div class="max-w-6xl mx-auto">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl">
            {{ t('vesselVisitStats.title') }}
          </h1>
          <RouterLink to="/vessels" class="text-sm text-blue-600 hover:underline">
            ← {{ t('vesselVisitStats.back') }}
          </RouterLink>
        </div>
      </div>

      <div v-if="visits.length === 0" class="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-500">
        {{ t('vesselVisitStats.empty') }}
      </div>

      <div v-else>
        <div
          v-if="selectedVisit && drillDown"
          data-test="drill-down"
          class="bg-blue-50 border border-blue-300 rounded-lg p-4 mb-6"
        >
          <div class="text-sm font-semibold text-gray-700">{{ t('vesselVisitStats.drillDown.title') }}</div>
          <div class="text-lg font-bold mt-1">
            {{ selectedVisit.vesselName }} ({{ selectedVisit.vesselId }}) — {{ selectedVisit.visitReference }}
          </div>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-3 mt-3 text-sm">
            <div>
              <div class="text-xs text-gray-500">{{ t('vesselVisitStats.drillDown.plannedDwell') }}</div>
              <div class="font-medium">{{ drillDown.plannedDays === null ? '—' : drillDown.plannedDays.toFixed(1) + ' d' }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ t('vesselVisitStats.drillDown.actualDwell') }}</div>
              <div class="font-medium">{{ drillDown.actualDays === null ? '—' : drillDown.actualDays.toFixed(1) + ' d' }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ t('vesselVisitStats.drillDown.delay') }}</div>
              <div class="font-medium" :class="{ 'text-red-600': drillDown.deltaHours !== null && drillDown.deltaHours > 0 }">
                {{ drillDown.deltaHours === null ? '—' : (drillDown.deltaHours > 0 ? '+' : '') + drillDown.deltaHours.toFixed(1) + ' h' }}
              </div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ t('vesselVisitStats.drillDown.etaAta') }}</div>
              <div class="font-medium">{{ drillDown.etaAtaDeltaHours === null ? '—' : drillDown.etaAtaDeltaHours.toFixed(1) + ' h' }}</div>
            </div>
            <div>
              <div class="text-xs text-gray-500">{{ t('vesselVisitStats.drillDown.etdAtd') }}</div>
              <div class="font-medium">{{ drillDown.etdAtdDeltaHours === null ? '—' : drillDown.etdAtdDeltaHours.toFixed(1) + ' h' }}</div>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div data-test="kpi-total-visits" class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-xs text-gray-500 uppercase">{{ t('vesselVisitStats.kpi.totalVisits') }}</div>
            <div class="text-2xl font-bold text-gray-900">{{ visits.length }}</div>
          </div>
          <div data-test="kpi-active-visits" class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-xs text-gray-500 uppercase">{{ t('vesselVisitStats.kpi.activeVisits') }}</div>
            <div class="text-2xl font-bold text-green-600">{{ phaseCounts.Active }}</div>
          </div>
          <div data-test="kpi-avg-dwell" class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-xs text-gray-500 uppercase">{{ t('vesselVisitStats.kpi.avgDwellDays') }}</div>
            <div class="text-2xl font-bold text-gray-900">{{ avgDwell === null ? '—' : avgDwell.toFixed(1) }}</div>
          </div>
          <div data-test="kpi-on-time" class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-xs text-gray-500 uppercase">{{ t('vesselVisitStats.kpi.onTimeRate') }}</div>
            <div class="text-2xl font-bold text-gray-900">{{ onTime === null ? '—' : (onTime * 100).toFixed(0) + '%' }}</div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-sm font-semibold text-gray-700 mb-2">{{ t('vesselVisitStats.chart.phaseDistribution') }}</div>
            <div style="height: 240px"><Pie :data="phaseChartData" :options="pieOptions" /></div>
          </div>
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-sm font-semibold text-gray-700 mb-2">{{ t('vesselVisitStats.chart.monthlyVisits') }}</div>
            <div style="height: 240px"><Bar :data="monthlyData" :options="verticalBarOptions" /></div>
          </div>
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-sm font-semibold text-gray-700 mb-2">{{ t('vesselVisitStats.chart.topVessels') }}</div>
            <div style="height: 240px"><Bar :data="topVesselsData" :options="horizontalBarOptions" /></div>
          </div>
          <div class="bg-white rounded-lg border border-gray-200 p-4">
            <div class="text-sm font-semibold text-gray-700 mb-2">{{ t('vesselVisitStats.chart.serviceDistribution') }}</div>
            <div style="height: 240px"><Bar :data="serviceData" :options="horizontalBarOptions" /></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRoute } from 'vue-router';
import { Pie, Bar } from 'vue-chartjs';
import {
  Chart,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js';
import { useVesselVisit } from '../composables/use.vessel-visit';
import {
  countByPhase,
  countByService,
  topVesselsByVisitCount,
  visitsPerMonth,
  averageDwellDays,
  onTimeRate,
  computeDwell,
} from '../utils/vessel-visit-stats';

Chart.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend);

const { t } = useI18n();
const route = useRoute();
// useVesselVisit() itself registers an onMounted that calls getVesselVisits().
// We do NOT add a second onMounted here — that would double-fetch.
const { visits } = useVesselVisit();

const phaseCounts = computed(() => countByPhase(visits.value));
const avgDwell = computed(() => averageDwellDays(visits.value));
const onTime = computed(() => onTimeRate(visits.value));

const selectedVisit = computed(() => {
  const id = route.query.visit;
  if (typeof id !== 'string') return null;
  return visits.value.find((v) => v.id === id) ?? null;
});

const drillDown = computed(() => (selectedVisit.value ? computeDwell(selectedVisit.value) : null));

const phaseChartData = computed(() => ({
  labels: ['Created', 'Active', 'Completed', 'Canceled'],
  datasets: [
    {
      data: [
        phaseCounts.value.Created,
        phaseCounts.value.Active,
        phaseCounts.value.Completed,
        phaseCounts.value.Canceled,
      ],
      backgroundColor: ['#3b82f6', '#16a34a', '#6b7280', '#dc2626'],
    },
  ],
}));

const monthlyData = computed(() => {
  const buckets = visitsPerMonth(visits.value, 12);
  return {
    labels: buckets.map((b) => b.yearMonth),
    datasets: [{ label: 'Visits', data: buckets.map((b) => b.count), backgroundColor: '#3b82f6' }],
  };
});

const topVesselsData = computed(() => {
  const items = topVesselsByVisitCount(visits.value, 5);
  return {
    labels: items.map((i) => i.vesselName),
    datasets: [{ label: 'Visits', data: items.map((i) => i.count), backgroundColor: '#3b82f6' }],
  };
});

const serviceData = computed(() => {
  const items = countByService(visits.value).slice(0, 5);
  return {
    labels: items.map((i) => i.service),
    datasets: [{ label: 'Visits', data: items.map((i) => i.count), backgroundColor: '#16a34a' }],
  };
});

const horizontalBarOptions = {
  indexAxis: 'y' as const,
  responsive: true,
  maintainAspectRatio: false,
};
const verticalBarOptions = { responsive: true, maintainAspectRatio: false };
const pieOptions = { responsive: true, maintainAspectRatio: false };
</script>
