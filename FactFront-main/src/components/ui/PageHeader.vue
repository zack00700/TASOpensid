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
  /* Tide : l'en-tête se pose sur le sol « ocean », séparé par un simple filet. */
  background: transparent;
  border-bottom: 1px solid rgba(42, 36, 30, 0.07);
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
  /* Inter en semi-gras : la hiérarchie Tide passe par la taille et
     l'interlettrage, pas par une serif. */
  font-size: 1.75rem;
  font-weight: 600;
  color: #2a241e;
  margin: 0;
  line-height: 1.15;
  letter-spacing: -0.035em;
}
.page-header-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.6rem;
  padding: 0 0.45rem;
  height: 1.4rem;
  background: rgba(90, 138, 171, 0.16);
  color: #3e6080;
  border-radius: 99px;
  font-size: 0.7rem;
  font-weight: 600;
  line-height: 1;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.page-header-subtitle {
  font-size: 0.8125rem;
  color: rgba(42, 36, 30, 0.6);
  margin: 0.375rem 0 0;
  max-width: 560px;
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
    font-size: 1.375rem;
  }
}
</style>
