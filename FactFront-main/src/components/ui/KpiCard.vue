<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  label:     string
  value:     string | number
  subLabel?: string
  trend?:    number          // positive = up, negative = down
  color?:    'blue' | 'amber' | 'emerald' | 'slate'
  clickable?: boolean
}>(), {
  color:     'blue',
  clickable: false,
})

defineEmits<{ click: [] }>()

const BORDER_COLORS: Record<string, string> = {
  blue:    '#3b82f6',
  amber:   '#f59e0b',
  emerald: '#10b981',
  slate:   '#64748b',
}

const ICON_COLORS: Record<string, string> = {
  blue:    '#eff6ff',
  amber:   '#fffbeb',
  emerald: '#f0fdf4',
  slate:   '#f8fafc',
}

const ICON_TEXT: Record<string, string> = {
  blue:    '#3b82f6',
  amber:   '#f59e0b',
  emerald: '#10b981',
  slate:   '#64748b',
}

const borderColor = computed(() => BORDER_COLORS[props.color])
const iconBg      = computed(() => ICON_COLORS[props.color])
const iconText    = computed(() => ICON_TEXT[props.color])

const trendLabel = computed(() => {
  if (props.trend === undefined || props.trend === null) return null
  const abs = Math.abs(props.trend)
  return props.trend > 0 ? `+${abs}%` : props.trend < 0 ? `-${abs}%` : null
})
const trendUp = computed(() => (props.trend ?? 0) > 0)
</script>

<template>
  <div
    class="kpi-card"
    :style="{ borderLeftColor: borderColor }"
    :class="{ 'kpi-card--clickable': clickable }"
    @click="clickable ? $emit('click') : undefined"
    role="article"
  >
    <div class="kpi-top">
      <div class="kpi-left">
        <p class="kpi-label">{{ label }}</p>
        <p class="kpi-value">{{ value }}</p>
        <div v-if="subLabel || trendLabel" class="kpi-sub">
          <span v-if="subLabel" class="kpi-sublabel">{{ subLabel }}</span>
          <span v-if="trendLabel" class="kpi-trend" :class="trendUp ? 'kpi-trend--up' : 'kpi-trend--down'">
            <svg v-if="trendUp"    viewBox="0 0 16 16" fill="currentColor"><path d="M8 3l5 5H3l5-5z"/></svg>
            <svg v-else            viewBox="0 0 16 16" fill="currentColor"><path d="M8 13L3 8h10l-5 5z"/></svg>
            {{ trendLabel }}
          </span>
        </div>
      </div>
      <div v-if="$slots.icon" class="kpi-icon" :style="{ background: iconBg, color: iconText }">
        <slot name="icon" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-card {
  /* Tide : surface de verre sur le sol « ocean ». */
  border-radius: 18px;
  padding: 1.1rem 1.25rem;
  background: linear-gradient(135deg, rgba(255, 253, 247, 0.95), rgba(252, 247, 238, 0.85));
  backdrop-filter: blur(22px) saturate(140%);
  -webkit-backdrop-filter: blur(22px) saturate(140%);
  border: 1px solid rgba(60, 50, 35, 0.10);
  border-left: 3px solid transparent;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset, 0 1px 2px rgba(60, 50, 35, 0.04),
    0 20px 50px -22px rgba(60, 50, 35, 0.18);
  user-select: none;
}
.kpi-card--clickable {
  cursor: pointer;
}
.kpi-card--clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset, 0 24px 60px -22px rgba(60, 50, 35, 0.26);
}
.kpi-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.kpi-left {
  min-width: 0;
}
.kpi-label {
  font-size: 0.66rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(42, 36, 30, 0.55);
  margin: 0 0 0.35rem;
}
.kpi-value {
  font-size: 1.5rem;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: #2a241e;
  font-variant-numeric: tabular-nums;
  margin: 0 0 0.35rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kpi-sub {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.kpi-sublabel {
  font-size: 0.7rem;
  color: rgba(42, 36, 30, 0.5);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.kpi-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 99px;
}
.kpi-trend svg {
  width: 10px;
  height: 10px;
}
.kpi-trend--up   { background: #f0fdf4; color: #16a34a; }
.kpi-trend--down { background: #fef2f2; color: #dc2626; }
.kpi-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
