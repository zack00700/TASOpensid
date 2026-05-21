<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue:    string
  placeholder?:  string
  filtersActive?: boolean
  loading?:      boolean
  debounce?:     number
}>(), {
  placeholder:   'Search…',
  filtersActive: false,
  loading:       false,
  debounce:      300,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'toggle-filters':    []
  'refresh':           []
}>()

const internalValue = ref(props.modelValue)
let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.modelValue, v => { internalValue.value = v })

function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  internalValue.value = v
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => emit('update:modelValue', v), props.debounce)
}

function clear() {
  internalValue.value = ''
  if (timer) clearTimeout(timer)
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="sb-row">
    <!-- Search input -->
    <div class="sb-input-wrap">
      <svg class="sb-icon-search" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd"/>
      </svg>
      <input
        type="text"
        :value="internalValue"
        @input="onInput"
        :placeholder="placeholder"
        class="sb-input"
        aria-label="Search"
      />
      <button v-if="internalValue" class="sb-clear" @click="clear" aria-label="Clear search">
        <svg viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
          <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
        </svg>
      </button>
    </div>

    <!-- Filter toggle -->
    <button
      class="sb-btn-filter"
      :class="{ 'sb-btn-filter--active': filtersActive }"
      @click="$emit('toggle-filters')"
      aria-label="Toggle filters"
    >
      <svg viewBox="0 0 20 20" fill="currentColor" class="sb-icon-filter">
        <path fill-rule="evenodd" d="M3 3a1 1 0 011-1h12a1 1 0 011 1v3a1 1 0 01-.293.707L13 10.414V17a1 1 0 01-.553.894l-4 2A1 1 0 017 19v-8.586L3.293 6.707A1 1 0 013 6V3z" clip-rule="evenodd"/>
      </svg>
      <span class="sb-btn-label">Filtres</span>
      <span v-if="filtersActive" class="sb-badge" />
    </button>

    <!-- Refresh slot -->
    <slot name="actions" />
  </div>
</template>

<style scoped>
.sb-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.sb-input-wrap {
  position: relative;
  flex: 1;
}
.sb-icon-search {
  position: absolute;
  left: 0.7rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1rem;
  height: 1rem;
  color: #94a3b8;
  pointer-events: none;
}
.sb-input {
  width: 100%;
  padding: 0.5rem 2rem 0.5rem 2.2rem;
  border: 1.5px solid #e2e8f0;
  border-radius: 9px;
  font-size: 0.875rem;
  color: #1e293b;
  background: #f8fafc;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.sb-input:focus {
  border-color: #3b82f6;
  background: #fff;
}
.sb-clear {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
  line-height: 0;
}
.sb-clear:hover { color: #475569; }

.sb-btn-filter {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 0.85rem;
  border: 1.5px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  color: #475569;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.sb-btn-filter:hover { background: #f8fafc; }
.sb-btn-filter--active {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #1d4ed8;
}
.sb-icon-filter {
  width: 0.95rem;
  height: 0.95rem;
  flex-shrink: 0;
}
.sb-btn-label {
  display: none;
}
.sb-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  background: #3b82f6;
  border-radius: 50%;
  border: 1.5px solid #fff;
}
@media (min-width: 640px) {
  .sb-btn-label { display: inline; }
}
</style>
