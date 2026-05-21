<script setup lang="ts">
export interface FilterChip {
  key:    string
  label:  string
  value?: string
}

defineProps<{
  chips: FilterChip[]
}>()

defineEmits<{
  remove:    [key: string, value?: string]
  'clear-all': []
}>()
</script>

<template>
  <div v-if="chips.length" class="fc-root">
    <span class="fc-prefix">Filtres actifs :</span>
    <div class="fc-list">
      <span
        v-for="chip in chips"
        :key="chip.key + (chip.value ?? '')"
        class="fc-chip"
      >
        {{ chip.label }}
        <button
          class="fc-remove"
          @click="$emit('remove', chip.key, chip.value)"
          aria-label="Retirer le filtre"
        >
          <svg viewBox="0 0 12 12" fill="currentColor" class="fc-x">
            <path d="M6 4.586L9.293 1.293a1 1 0 111.414 1.414L7.414 6l3.293 3.293a1 1 0 01-1.414 1.414L6 7.414l-3.293 3.293a1 1 0 01-1.414-1.414L4.586 6 1.293 2.707A1 1 0 012.707 1.293L6 4.586z"/>
          </svg>
        </button>
      </span>
      <button class="fc-clear" @click="$emit('clear-all')">Tout effacer</button>
    </div>
  </div>
</template>

<style scoped>
.fc-root {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.6rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.fc-prefix {
  font-size: 0.75rem;
  font-weight: 600;
  color: #64748b;
  padding-top: 3px;
  white-space: nowrap;
}
.fc-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: center;
}
.fc-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 10px;
  background: #e0e7ff;
  color: #3730a3;
  border-radius: 99px;
  font-size: 0.75rem;
  font-weight: 500;
  border: 1px solid #c7d2fe;
}
.fc-remove {
  background: none;
  border: none;
  cursor: pointer;
  padding: 1px;
  color: #6366f1;
  line-height: 0;
  border-radius: 50%;
  transition: background 0.1s;
}
.fc-remove:hover { background: #c7d2fe; }
.fc-x { width: 10px; height: 10px; }
.fc-clear {
  font-size: 0.72rem;
  color: #64748b;
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: underline;
  padding: 0 2px;
}
.fc-clear:hover { color: #1e293b; }
</style>
