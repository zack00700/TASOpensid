<template>
  <div>
    <!-- ── FAB ────────────────────────────────────────────── -->
    <button class="askai-fab" @click="openModal" :aria-label="t('askAiFab.label.askAi')">
      <svg class="askai-fab-star" viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z"/>
      </svg>
      {{ t('askAiFab.askAi') }}
    </button>

    <!-- ── Question modal ────────────────────────────────── -->
    <Teleport to="body">
      <Transition name="askai-fade">
        <div v-if="showModal" class="askai-backdrop" @click.self="closeModal">
          <div class="askai-dialog" role="dialog" aria-modal="true">
            <div class="askai-dialog-head">
              <div class="askai-dialog-headleft">
                <span class="askai-dialog-spark">✦</span>
                <h2 class="askai-dialog-title">{{ t('askAiFab.askAi') }}</h2>
              </div>
              <button class="askai-dialog-x" @click="closeModal" :aria-label="t('common.close')">✕</button>
            </div>
            <p class="askai-dialog-hint">
              {{ t('askAiFab.dialog.hint') }}
            </p>
            <textarea
              v-model="question"
              ref="textareaRef"
              class="askai-textarea"
              :placeholder="t('askAiFab.placeholder.question')"
              rows="3"
              @keydown.ctrl.enter.prevent="submit"
              @keydown.meta.enter.prevent="submit"
            />
            <div class="askai-chips">
              <button
                v-for="s in SUGGESTIONS"
                :key="s"
                class="askai-chip"
                @click="question = s"
              >{{ s }}</button>
            </div>
            <div class="askai-dialog-foot">
              <button class="askai-btn-cancel" @click="closeModal" :disabled="loading">{{ t('common.cancel') }}</button>
              <button class="askai-btn-go" @click="submit" :disabled="loading || !question.trim()">
                <span v-if="loading" class="askai-spin" />
                {{ loading ? t('askAiFab.button.analyzing') : t('askAiFab.button.generate') }}
              </button>
            </div>
            <div v-if="errorMsg" class="askai-error">{{ errorMsg }}</div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ── Report overlay ────────────────────────────────── -->
    <Teleport to="body">
      <Transition name="askai-slide">
        <div v-if="reportSpec" class="askai-report-overlay">

          <!-- Toolbar (not printed) -->
          <div class="askai-toolbar no-print">
            <div class="askai-toolbar-brand">
              <span class="askai-toolbar-spark">✦</span>
              <span class="askai-toolbar-label">{{ t('askAiFab.toolbar.aiReport') }}</span>
            </div>
            <div class="askai-toolbar-actions">
              <button class="askai-tbtn" @click="newQuestion">{{ t('askAiFab.toolbar.newQuestion') }}</button>
              <button class="askai-tbtn" @click="exportCsv">{{ t('askAiFab.toolbar.exportCsv') }}</button>
              <button class="askai-tbtn" @click="printReport">{{ t('askAiFab.toolbar.printPdf') }}</button>
              <button class="askai-tbtn askai-tbtn-close" @click="closeReport">✕</button>
            </div>
          </div>

          <!-- Scrollable report -->
          <div class="askai-report-scroll" ref="reportRef">
            <div class="askai-report-page">

              <!-- ── Report header ───────────────────── -->
              <div class="askai-rhead">
                <div class="askai-rhead-left">
                  <div class="askai-rhead-tag">{{ t('askAiFab.report.tag') }}</div>
                  <h1 class="askai-rhead-title">{{ reportTitle }}</h1>
                  <div class="askai-rhead-q">{{ t('askAiFab.report.questionQuoted', { question: lastQuestion }) }}</div>
                </div>
                <div class="askai-rhead-right">
                  <div class="askai-rhead-date">{{ reportDate }}</div>
                  <div class="askai-rhead-source">{{ t('askAiFab.report.source') }}</div>
                </div>
              </div>

              <!-- ── AI Insight ──────────────────────── -->
              <div v-if="reportSpec.answer" class="askai-insight">
                <div class="askai-insight-head">
                  <svg class="askai-insight-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>
                  </svg>
                  <span>{{ t('askAiFab.insight.title') }}</span>
                </div>
                <p class="askai-insight-text">{{ reportSpec.answer }}</p>
              </div>

              <!-- ── KPI Cards ───────────────────────── -->
              <div v-if="kpiCards.length" class="askai-kpis">
                <div v-for="kpi in kpiCards" :key="kpi.label" class="askai-kpi">
                  <div class="askai-kpi-val">{{ kpi.formatted }}</div>
                  <div class="askai-kpi-lbl">{{ kpi.label }}</div>
                </div>
              </div>

              <!-- ── Chart ──────────────────────────── -->
              <div v-if="hasChartData" class="askai-chart-section">
                <h2 class="askai-section-h">{{ chartTitle }}</h2>
                <div ref="chartEl" class="askai-chart" />
              </div>

              <!-- ── Data table ─────────────────────── -->
              <div v-if="tCols.length" class="askai-table-section">
                <div class="askai-table-titlerow">
                  <h2 class="askai-section-h">{{ t('askAiFab.table.title') }}</h2>
                  <span class="askai-table-badge">{{ t('askAiFab.table.recordsCount', { count: tRows.length }) }}</span>
                </div>
                <div class="askai-table-wrap">
                  <table class="askai-table">
                    <thead>
                      <tr>
                        <th class="askai-th askai-th-num">#</th>
                        <th
                          v-for="col in tCols"
                          :key="col"
                          :class="thClass(col)"
                        >{{ niceHeader(col) }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(row, ri) in tRows"
                        :key="ri"
                        :class="ri % 2 === 0 ? 'askai-row-even' : 'askai-row-odd'"
                      >
                        <td class="askai-td askai-td-num">{{ ri + 1 }}</td>
                        <td
                          v-for="(col, ci) in tCols"
                          :key="col"
                          :class="tdClass(col)"
                        >
                          <span v-if="isStatusCol(col)" :class="statusBadge(cellVal(row, ci))">
                            {{ cellVal(row, ci) }}
                          </span>
                          <template v-else>{{ fmtCell(cellVal(row, ci), col) }}</template>
                        </td>
                      </tr>
                    </tbody>
                    <tfoot v-if="totalsRow.some(v => v !== null)">
                      <tr class="askai-tfoot">
                        <td class="askai-td askai-td-num">∑</td>
                        <td
                          v-for="(tot, ci) in totalsRow"
                          :key="ci"
                          :class="tdClass(tCols[ci])"
                        >
                          <strong v-if="tot !== null">{{ fmtAmount(tot) }}</strong>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </div>

              <!-- ── Footer ─────────────────────────── -->
              <div class="askai-rfooter">
                <span>{{ t('askAiFab.footer.poweredBy') }}</span>
                <span class="askai-dot">·</span>
                <span>{{ t('askAiFab.footer.system') }}</span>
                <span class="askai-dot">·</span>
                <span>{{ reportDate }}</span>
              </div>

            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '../stores/authStore';

const { t } = useI18n();

// ─── Types ──────────────────────────────────────────────────
interface Dataset { name?: string; data?: number[] }
interface AskAiSpec {
  title?: string | null;
  answer?: string | null;
  chart?: { type?: string; labels?: string[]; datasets?: Dataset[] } | null;
  table?: { columns?: string[]; rows?: (string | number)[][] } | null;
}

// ─── Constants ──────────────────────────────────────────────
const API_BASE = (import.meta as any).env?.VITE_API_URL || 'http://localhost:8080/api';

const SUGGESTIONS = computed(() => [
  t('askAiFab.suggestion.monthlyTrend'),
  t('askAiFab.suggestion.topClients'),
  t('askAiFab.suggestion.statusBreakdown'),
  t('askAiFab.suggestion.blsThisQuarter'),
  t('askAiFab.suggestion.topPorts'),
]);

// ─── State ──────────────────────────────────────────────────
const showModal  = ref(false);
const question   = ref('');
const loading    = ref(false);
const errorMsg   = ref('');
const reportSpec = ref<AskAiSpec | null>(null);
const lastQuestion = ref('');
const textareaRef  = ref<HTMLTextAreaElement | null>(null);
const chartEl      = ref<HTMLElement | null>(null);
const reportRef    = ref<HTMLElement | null>(null);
let echartsInst: any = null;

const authStore = useAuthStore();

// ─── Modal ──────────────────────────────────────────────────
function openModal() {
  showModal.value = true;
  nextTick(() => textareaRef.value?.focus());
}
function closeModal() {
  if (loading.value) return;
  showModal.value = false;
  question.value = '';
  errorMsg.value = '';
}

// ─── Report ─────────────────────────────────────────────────
function closeReport() {
  echartsInst?.dispose();
  echartsInst = null;
  reportSpec.value = null;
}
function newQuestion() { closeReport(); openModal(); }

// ─── Submit ─────────────────────────────────────────────────
async function submit() {
  if (loading.value || !question.value.trim()) return;
  loading.value = true;
  errorMsg.value = '';
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (authStore.token) headers.Authorization = `Bearer ${authStore.token}`;
    const res = await fetch(`${API_BASE}/ask-ai`, {
      method: 'POST', headers,
      body: JSON.stringify({ question: question.value }),
    });
    if (!res.ok) throw new Error(await res.text());
    const spec: AskAiSpec = await res.json();
    lastQuestion.value = question.value;
    // Close modal inline (can't call closeModal() — it guards on loading=true)
    showModal.value = false;
    question.value = '';
    errorMsg.value = '';
    reportSpec.value = spec;
    await nextTick();
    await loadChart(spec);
  } catch (e: any) {
    errorMsg.value = e.message || t('askAiFab.error.requestFailed');
  } finally {
    loading.value = false;
  }
}

// ─── Computed – report metadata ──────────────────────────────
const reportTitle = computed(() => reportSpec.value?.title || t('askAiFab.report.defaultTitle'));
const reportDate  = computed(() =>
  new Date().toLocaleString('fr-FR', {
    day: '2-digit', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
);

// ─── KPI cards ───────────────────────────────────────────────
const kpiCards = computed(() => {
  const ch = reportSpec.value?.chart;
  if (!ch || ch.type !== 'kpi') return [];
  return (ch.labels ?? []).map((label, i) => ({
    label,
    value: ch.datasets?.[0]?.data?.[i] ?? 0,
    formatted: fmtAmount(ch.datasets?.[0]?.data?.[i] ?? 0),
  }));
});

// ─── Chart ───────────────────────────────────────────────────
const hasChartData = computed(() => {
  const ch = reportSpec.value?.chart;
  if (!ch || ch.type === 'kpi') return false;
  return (ch.labels?.length ?? 0) > 0 && (ch.datasets?.[0]?.data?.length ?? 0) > 0;
});

const chartTitle = computed(() => {
  switch (reportSpec.value?.chart?.type) {
    case 'line': return t('askAiFab.chart.titleLine');
    case 'pie':  return t('askAiFab.chart.titlePie');
    default:     return t('askAiFab.chart.titleDefault');
  }
});

// ─── Table ───────────────────────────────────────────────────
const tCols = computed(() => reportSpec.value?.table?.columns ?? []);
const tRows = computed(() => reportSpec.value?.table?.rows ?? []);

const totalsRow = computed(() =>
  tCols.value.map((col, ci) => {
    if (!isAmountCol(col)) return null;
    return tRows.value.reduce((sum, row) => {
      const v = parseFloat(String(cellVal(row, ci)).replace(/[^0-9.-]/g, ''));
      return sum + (isNaN(v) ? 0 : v);
    }, 0);
  })
);

// ─── Cell helpers ────────────────────────────────────────────
function cellVal(row: any, ci: number): string {
  if (Array.isArray(row)) return String(row[ci] ?? '');
  return '';
}

function isAmountCol(col: string)  { return /amount|total|revenue|montant|price|tarif/i.test(col); }
function isDateCol(col: string)    { return /date|createdat|created_at|issuedat|updatedat/i.test(col); }
function isStatusCol(col: string)  { return /status|state|statut/i.test(col); }
function isNumRefCol(col: string)  { return /finalnumber|number|blnumber|ref|no\b/i.test(col); }

const HEADER_KEY_MAP: Record<string, string> = {
  finalNumber: 'askAiFab.header.finalNumber',
  createdDate: 'askAiFab.header.createdDate',
  customerName: 'askAiFab.header.customerName',
  amount: 'askAiFab.header.amount',
  status: 'askAiFab.header.status',
  blNumber: 'askAiFab.header.blNumber',
  shipper: 'askAiFab.header.shipper',
  consignee: 'askAiFab.header.consignee',
  portOfLoading: 'askAiFab.header.portOfLoading',
  portOfDischarge: 'askAiFab.header.portOfDischarge',
  vessel: 'askAiFab.header.vessel',
  voyage: 'askAiFab.header.voyage',
};
function niceHeader(col: string): string {
  const key = HEADER_KEY_MAP[col];
  if (key) return t(key);
  return col.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
}

function thClass(col: string) {
  if (isAmountCol(col)) return 'askai-th askai-th-right';
  if (isNumRefCol(col)) return 'askai-th askai-th-mono';
  return 'askai-th';
}
function tdClass(col: string) {
  if (isAmountCol(col)) return 'askai-td askai-td-right askai-td-amount';
  if (isNumRefCol(col)) return 'askai-td askai-td-mono';
  if (isDateCol(col))   return 'askai-td askai-td-date';
  return 'askai-td';
}

function statusBadge(val: string): string {
  const v = (val ?? '').toLowerCase();
  if (/paid|completed|active|done|approved|actif/i.test(v))  return 'askai-badge askai-badge-green';
  if (/pending|progress|draft|clarifying|review/i.test(v))   return 'askai-badge askai-badge-amber';
  if (/cancel|reject|overdue|failed|error/i.test(v))         return 'askai-badge askai-badge-red';
  return 'askai-badge askai-badge-slate';
}

function fmtAmount(val: any): string {
  const n = parseFloat(String(val ?? '0').replace(/[^0-9.-]/g, ''));
  if (isNaN(n)) return String(val ?? '');
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency', currency: 'EUR', maximumFractionDigits: 0,
  }).format(n);
}

function fmtDate(val: any): string {
  if (!val) return '';
  const s = String(val);
  const iso = s.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (iso) return `${iso[3]}/${iso[2]}/${iso[1]}`;
  const d = new Date(s);
  return isNaN(d.getTime()) ? s : d.toLocaleDateString('fr-FR');
}

function fmtCell(val: any, col: string): string {
  if (isAmountCol(col)) return fmtAmount(val);
  if (isDateCol(col))   return fmtDate(val);
  return String(val ?? '');
}

function fmtAxis(v: number): string {
  if (Math.abs(v) >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M€`;
  if (Math.abs(v) >= 1_000)     return `${(v / 1_000).toFixed(0)}k€`;
  return `${v}€`;
}

// ─── ECharts ─────────────────────────────────────────────────
async function loadChart(spec: AskAiSpec) {
  if (!hasChartData.value || !chartEl.value) return;
  const win = window as any;
  if (!win.echarts) {
    await new Promise<void>((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js';
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('ECharts failed to load'));
      document.head.appendChild(s);
    });
  }
  const echarts = win.echarts;
  if (echartsInst) echartsInst.dispose();
  echartsInst = echarts.init(chartEl.value, null, { renderer: 'canvas' });
  echartsInst.setOption(buildOption(spec, echarts));
  window.addEventListener('resize', onResize);
}

function onResize() { echartsInst?.resize(); }
onUnmounted(() => { echartsInst?.dispose(); window.removeEventListener('resize', onResize); });

function buildOption(spec: AskAiSpec, echarts: any): object {
  const ch     = spec.chart!;
  const labels  = ch.labels ?? [];
  const ds      = ch.datasets ?? [];
  const type    = ch.type ?? 'bar';
  const palette = ['#1d4ed8', '#059669', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#be185d'];

  const tooltipFmt = (params: any[]) =>
    (Array.isArray(params) ? params : [params])
      .map((p: any) => `${p.marker} <span style="color:#374151">${p.seriesName || p.name}</span>: <b>${fmtAmount(p.value)}</b>`)
      .join('<br>');

  const baseYAxis = {
    type: 'value',
    axisLabel: { formatter: (v: number) => fmtAxis(v), fontSize: 11, color: '#6b7280' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
    axisLine: { show: false },
    axisTick: { show: false },
  };

  if (type === 'pie') {
    return {
      tooltip: { trigger: 'item', formatter: (p: any) => `${p.marker} <b>${p.name}</b><br/>${fmtAmount(p.value)} (${p.percent?.toFixed(1)}%)` },
      legend: { type: 'scroll', orient: 'horizontal', bottom: 0, textStyle: { fontSize: 11, color: '#374151' } },
      color: palette,
      series: [{
        type: 'pie', radius: ['38%', '68%'], center: ['50%', '46%'],
        data: labels.map((l, i) => ({ name: l, value: ds[0]?.data?.[i] ?? 0 })),
        label: { formatter: (p: any) => `{name|${p.name}}\n{pct|${p.percent?.toFixed(0)}%}`, rich: { name: { fontSize: 11, color: '#374151' }, pct: { fontSize: 12, fontWeight: 'bold', color: '#1e40af' } } },
        labelLine: { length: 12, length2: 8 },
        emphasis: { scale: true, scaleSize: 6, itemStyle: { shadowBlur: 16, shadowColor: 'rgba(0,0,0,0.12)' } },
      }],
    };
  }

  if (type === 'line') {
    return {
      tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#e2e8f0', textStyle: { color: '#374151' }, formatter: tooltipFmt },
      legend: ds.length > 1 ? { bottom: 0, textStyle: { fontSize: 11 } } : { show: false },
      grid: { left: 80, right: 20, top: 20, bottom: labels.length > 8 ? 75 : 40 },
      xAxis: { type: 'category', data: labels, axisLabel: { rotate: labels.length > 8 ? 40 : 0, fontSize: 11, color: '#6b7280' }, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisTick: { show: false } },
      yAxis: baseYAxis,
      color: palette,
      series: ds.map((d, i) => ({
        name: d.name || t('askAiFab.chart.seriesDefault'), type: 'line', smooth: true, data: d.data ?? [],
        lineStyle: { width: 2.5, color: palette[i % palette.length] },
        symbol: 'circle', symbolSize: 7,
        itemStyle: { color: palette[i % palette.length], borderWidth: 2, borderColor: '#fff' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: palette[i % palette.length] + '28' },
          { offset: 1, color: palette[i % palette.length] + '05' },
        ])},
      })),
    };
  }

  // Bar (default)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(30,64,175,0.04)' } }, backgroundColor: '#fff', borderColor: '#e2e8f0', textStyle: { color: '#374151' }, formatter: tooltipFmt },
    legend: ds.length > 1 ? { bottom: 0, textStyle: { fontSize: 11 } } : { show: false },
    grid: { left: 80, right: 20, top: 20, bottom: labels.length > 6 ? 75 : 40 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: labels.length > 6 ? 35 : 0, fontSize: 11, color: '#6b7280', interval: 0 }, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisTick: { show: false } },
    yAxis: baseYAxis,
    color: palette,
    series: ds.map((d, i) => ({
      name: d.name || t('askAiFab.chart.valueDefault'), type: 'bar', data: d.data ?? [], barMaxWidth: 60,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: palette[i % palette.length] },
          { offset: 1, color: palette[i % palette.length] + 'bb' },
        ]),
        borderRadius: [5, 5, 0, 0],
      },
      label: {
        show: labels.length <= 12,
        position: 'top',
        formatter: (p: any) => fmtAxis(p.value),
        fontSize: 10, fontWeight: '600', color: '#475569',
      },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.15)' } },
    })),
  };
}

// ─── Export CSV ──────────────────────────────────────────────
function exportCsv() {
  const cols = tCols.value;
  if (!cols.length) return;
  const lines = [cols.map(niceHeader).join(',')];
  for (const row of tRows.value) {
    lines.push(cols.map((_, i) => `"${String(cellVal(row, i)).replace(/"/g, '""')}"`).join(','));
  }
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url  = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${reportTitle.value.replace(/[^a-zA-Z0-9]/g, '_')}_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function printReport() { window.print(); }
</script>

<style scoped>
/* ── FAB ──────────────────────────────────────────────── */
.askai-fab {
  position: fixed;
  top: 1.25rem;
  right: 1.25rem;
  z-index: 9000;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.55rem 1rem;
  background: linear-gradient(135deg, #1d4ed8, #4f46e5);
  color: #fff;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  border: none;
  border-radius: 9999px;
  box-shadow: 0 4px 14px rgba(29, 78, 216, 0.35);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.askai-fab:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(29, 78, 216, 0.45); }
.askai-fab-star { width: 14px; height: 14px; }

/* ── Transitions ──────────────────────────────────────── */
.askai-fade-enter-active, .askai-fade-leave-active { transition: opacity 0.2s; }
.askai-fade-enter-from, .askai-fade-leave-to { opacity: 0; }
.askai-slide-enter-active { transition: opacity 0.25s, transform 0.25s; }
.askai-slide-leave-active { transition: opacity 0.2s; }
.askai-slide-enter-from { opacity: 0; transform: translateY(16px); }
.askai-slide-leave-to   { opacity: 0; }

/* ── Backdrop ─────────────────────────────────────────── */
.askai-backdrop {
  position: fixed; inset: 0; z-index: 9500;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  padding: 1rem;
}

/* ── Dialog ───────────────────────────────────────────── */
.askai-dialog {
  width: 100%; max-width: 520px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.18);
  padding: 1.5rem;
  display: flex; flex-direction: column; gap: 1rem;
}
.askai-dialog-head {
  display: flex; align-items: center; gap: 0.6rem;
}
.askai-dialog-headleft { display: flex; align-items: center; gap: 0.5rem; flex: 1; }
.askai-dialog-spark    { font-size: 1.1rem; color: #1d4ed8; }
.askai-dialog-title    { font-size: 1.1rem; font-weight: 700; color: #0f172a; margin: 0; }
.askai-dialog-x        { background: none; border: none; cursor: pointer; color: #94a3b8; font-size: 1rem; padding: 0.25rem; border-radius: 6px; }
.askai-dialog-x:hover  { color: #334155; background: #f1f5f9; }
.askai-dialog-hint     { font-size: 0.83rem; color: #64748b; margin: 0; }
.askai-textarea {
  width: 100%; padding: 0.75rem; border: 1.5px solid #e2e8f0;
  border-radius: 10px; font-size: 0.9rem; color: #1e293b;
  resize: vertical; background: #f8fafc;
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.askai-textarea:focus  { outline: none; border-color: #3b82f6; background: #fff; }
.askai-chips           { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.askai-chip {
  padding: 0.3rem 0.7rem; background: #f1f5f9; border: 1px solid #e2e8f0;
  border-radius: 9999px; font-size: 0.75rem; color: #475569; cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.askai-chip:hover      { background: #dbeafe; border-color: #93c5fd; color: #1d4ed8; }
.askai-dialog-foot     { display: flex; justify-content: flex-end; gap: 0.6rem; }
.askai-btn-cancel {
  padding: 0.55rem 1rem; border: 1.5px solid #e2e8f0; border-radius: 8px;
  background: #fff; color: #64748b; font-size: 0.875rem; cursor: pointer;
}
.askai-btn-cancel:hover { background: #f8fafc; }
.askai-btn-go {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.55rem 1.25rem;
  background: linear-gradient(135deg, #1d4ed8, #4f46e5);
  color: #fff; font-size: 0.875rem; font-weight: 600;
  border: none; border-radius: 8px; cursor: pointer;
  transition: opacity 0.15s;
}
.askai-btn-go:disabled  { opacity: 0.55; cursor: not-allowed; }
.askai-spin {
  width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff; border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.askai-error           { font-size: 0.8rem; color: #dc2626; margin: 0; }

/* ── Report overlay ───────────────────────────────────── */
.askai-report-overlay {
  position: fixed; inset: 0; z-index: 9600;
  background: #f1f5f9;
  display: flex; flex-direction: column;
}

/* ── Toolbar ──────────────────────────────────────────── */
.askai-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0.6rem 1.5rem;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  flex-shrink: 0;
}
.askai-toolbar-brand   { display: flex; align-items: center; gap: 0.5rem; font-weight: 700; font-size: 0.9rem; color: #1e293b; }
.askai-toolbar-spark   { color: #1d4ed8; }
.askai-toolbar-actions { display: flex; align-items: center; gap: 0.5rem; }
.askai-tbtn {
  padding: 0.35rem 0.8rem; background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 7px; font-size: 0.78rem; color: #475569; cursor: pointer;
  transition: background 0.12s;
}
.askai-tbtn:hover      { background: #f1f5f9; color: #1e293b; }
.askai-tbtn-close      { color: #ef4444; border-color: #fecaca; background: #fff5f5; }
.askai-tbtn-close:hover { background: #fee2e2; }

/* ── Report scroll ────────────────────────────────────── */
.askai-report-scroll   { flex: 1; overflow-y: auto; padding: 2rem 1rem; }
.askai-report-page {
  max-width: 1060px; margin: 0 auto;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.06);
  padding: 2.5rem;
  display: flex; flex-direction: column; gap: 2rem;
}

/* ── Report header ────────────────────────────────────── */
.askai-rhead {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 2px solid #f1f5f9;
}
.askai-rhead-tag {
  display: inline-block; padding: 0.2rem 0.7rem;
  background: #dbeafe; color: #1d4ed8;
  border-radius: 9999px; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.05em;
  text-transform: uppercase; margin-bottom: 0.5rem;
}
.askai-rhead-title     { font-size: 1.6rem; font-weight: 800; color: #0f172a; margin: 0 0 0.3rem; line-height: 1.2; }
.askai-rhead-q         { font-size: 0.82rem; color: #94a3b8; font-style: italic; }
.askai-rhead-right     { text-align: right; flex-shrink: 0; }
.askai-rhead-date      { font-size: 0.82rem; font-weight: 600; color: #475569; }
.askai-rhead-source    { font-size: 0.75rem; color: #94a3b8; margin-top: 0.25rem; }

/* ── Insight box ──────────────────────────────────────── */
.askai-insight {
  background: linear-gradient(135deg, #eff6ff, #f0fdf4);
  border: 1px solid #bfdbfe;
  border-left: 4px solid #1d4ed8;
  border-radius: 10px;
  padding: 1rem 1.25rem;
}
.askai-insight-head    { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.4rem; }
.askai-insight-icon    { width: 16px; height: 16px; color: #1d4ed8; flex-shrink: 0; }
.askai-insight-head span { font-size: 0.78rem; font-weight: 700; color: #1d4ed8; text-transform: uppercase; letter-spacing: 0.05em; }
.askai-insight-text    { margin: 0; font-size: 0.9rem; color: #1e3a5f; line-height: 1.6; }

/* ── KPI cards ────────────────────────────────────────── */
.askai-kpis            { display: flex; gap: 1rem; flex-wrap: wrap; }
.askai-kpi {
  flex: 1; min-width: 160px;
  background: linear-gradient(135deg, #1e3a8a 0%, #1d4ed8 100%);
  border-radius: 12px; padding: 1.25rem 1.5rem;
  color: #fff;
}
.askai-kpi-val         { font-size: 1.8rem; font-weight: 800; letter-spacing: -0.02em; }
.askai-kpi-lbl         { font-size: 0.78rem; opacity: 0.8; margin-top: 0.25rem; font-weight: 500; }

/* ── Section heading ──────────────────────────────────── */
.askai-section-h       { font-size: 1rem; font-weight: 700; color: #1e293b; margin: 0 0 1rem; }

/* ── Chart ────────────────────────────────────────────── */
.askai-chart-section   { }
.askai-chart           { width: 100%; height: 380px; }

/* ── Table ────────────────────────────────────────────── */
.askai-table-titlerow  { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; }
.askai-table-badge {
  font-size: 0.75rem; padding: 0.2rem 0.6rem;
  background: #f1f5f9; border: 1px solid #e2e8f0;
  border-radius: 9999px; color: #64748b;
}
.askai-table-wrap      { overflow-x: auto; border-radius: 8px; border: 1px solid #f1f5f9; }
.askai-table           { width: 100%; border-collapse: collapse; font-size: 0.82rem; min-width: 600px; }
.askai-th {
  padding: 0.65rem 0.9rem; text-align: left;
  font-size: 0.72rem; font-weight: 700; color: #64748b;
  text-transform: uppercase; letter-spacing: 0.04em;
  background: #f8fafc; border-bottom: 2px solid #e2e8f0;
  white-space: nowrap;
}
.askai-th-right        { text-align: right; }
.askai-th-mono         { font-family: theme('fontFamily.mono'); }
.askai-th-num          { text-align: center; width: 2.5rem; }
.askai-td              { padding: 0.6rem 0.9rem; color: #334155; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }
.askai-td-num          { text-align: center; color: #94a3b8; font-size: 0.75rem; }
.askai-td-right        { text-align: right; }
.askai-td-mono         { font-family: theme('fontFamily.mono'); font-size: 0.8rem; color: #1e293b; }
.askai-td-amount       { font-weight: 600; color: #1e3a8a; }
.askai-td-date         { color: #64748b; white-space: nowrap; }
.askai-row-even        { background: #fff; }
.askai-row-odd         { background: #f8fafc; }
.askai-row-even:hover, .askai-row-odd:hover { background: #eff6ff; }
.askai-tfoot           { background: #f8fafc; }
.askai-tfoot .askai-td { border-top: 2px solid #e2e8f0; border-bottom: none; color: #0f172a; }

/* ── Status badges ────────────────────────────────────── */
.askai-badge           { display: inline-flex; align-items: center; padding: 0.15rem 0.55rem; border-radius: 9999px; font-size: 0.72rem; font-weight: 600; }
.askai-badge-green     { background: #dcfce7; color: #15803d; }
.askai-badge-amber     { background: #fef3c7; color: #b45309; }
.askai-badge-red       { background: #fee2e2; color: #dc2626; }
.askai-badge-slate     { background: #f1f5f9; color: #475569; }

/* ── Footer ───────────────────────────────────────────── */
.askai-rfooter {
  display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
  padding-top: 1rem; border-top: 1px solid #f1f5f9;
  font-size: 0.72rem; color: #94a3b8;
}
.askai-dot             { color: #cbd5e1; }

/* ── Print CSS ────────────────────────────────────────── */
@media print {
  .no-print          { display: none !important; }
  .askai-report-overlay { position: static; background: white; }
  .askai-report-scroll  { overflow: visible; padding: 0; }
  .askai-report-page    { box-shadow: none; padding: 1rem; max-width: none; }
  .askai-chart          { height: 280px; }
}
</style>
