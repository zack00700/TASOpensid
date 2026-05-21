<script setup lang="ts">
defineProps<{
  title:     string
  subtitle?: string
  count?:    number | null
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header-inner">
      <!-- Title block -->
      <div class="page-header-title-block">
        <div class="page-header-title-row">
          <h1 class="page-header-title">{{ title }}</h1>
          <span v-if="count != null" class="page-header-count">{{ count }}</span>
        </div>
        <p v-if="subtitle" class="page-header-subtitle">{{ subtitle }}</p>
      </div>

      <!-- Actions slot -->
      <div v-if="$slots.actions" class="page-header-actions">
        <slot name="actions" />
      </div>
    </div>

    <!-- KPI cards slot -->
    <div v-if="$slots.kpi" class="page-header-kpi">
      <slot name="kpi" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 1.5rem 1.5rem 0;
}
.page-header-inner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
}
.page-header-title-block {
  min-width: 0;
}
.page-header-title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
.page-header-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  line-height: 1.2;
  letter-spacing: -0.025em;
}
.page-header-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.6rem;
  padding: 0 0.45rem;
  height: 1.4rem;
  background: #f1f5f9;
  color: #64748b;
  border-radius: 99px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
}
.page-header-subtitle {
  font-size: 0.875rem;
  color: #64748b;
  margin: 0.25rem 0 0;
}
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.page-header-kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  padding-bottom: 1.5rem;
}
@media (max-width: 640px) {
  .page-header {
    padding: 1rem 1rem 0;
  }
  .page-header-kpi {
    grid-template-columns: 1fr 1fr;
    gap: 0.75rem;
  }
  .page-header-title {
    font-size: 1.2rem;
  }
}
</style>
