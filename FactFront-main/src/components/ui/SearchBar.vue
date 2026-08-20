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
  color: rgba(42,36,30,0.45);
  pointer-events: none;
}
.sb-input {
  width: 100%;
  padding: 0.5rem 2rem 0.5rem 2.2rem;
  border: 1.5px solid rgba(60,50,35,0.12);
  border-radius: 9px;
  font-size: 0.875rem;
  color: #2a241e;
  background: rgba(255,253,247,0.55);
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.sb-input:focus {
  border-color: #5a8aab;
  background: rgba(255,253,247,0.92);
}
.sb-clear {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  color: rgba(42,36,30,0.45);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
  line-height: 0;
}
.sb-clear:hover { color: rgba(42,36,30,0.70); }

.sb-btn-filter {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 0.85rem;
  border: 1.5px solid rgba(60,50,35,0.12);
  border-radius: 9px;
  background: rgba(255,253,247,0.92);
  color: rgba(42,36,30,0.70);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.sb-btn-filter:hover { background: rgba(255,253,247,0.55); }
.sb-btn-filter--active {
  border-color: #5a8aab;
  background: rgba(90,138,171,0.10);
  color: #3e6080;
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
  background: #5a8aab;
  border-radius: 50%;
  border: 1.5px solid #fff;
}
@media (min-width: 640px) {
  .sb-btn-label { display: inline; }
}
</style>
