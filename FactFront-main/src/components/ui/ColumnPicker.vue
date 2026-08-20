<template>
  <div class="cp-wrap">
    <button
      type="button"
      class="cp-trigger"
      :aria-expanded="open"
      aria-haspopup="true"
      @click="open = !open"
    >
      <Columns3 class="w-4 h-4" />
      <span class="cp-label">Colonnes</span>
      <span v-if="hiddenCount" class="cp-count">{{ hiddenCount }}</span>
    </button>

    <div v-if="open" class="cp-panel" role="menu">
      <div class="cp-header">
        <span class="cp-title">Colonnes visibles</span>
        <button type="button" class="cp-reset" @click="handleReset">Réinitialiser</button>
      </div>
      <ul class="cp-list">
        <li v-for="col in toggleable" :key="col.key" class="cp-item">
          <label class="cp-check">
            <input
              type="checkbox"
              :checked="isVisible(col.key)"
              @change="toggle(col.key)"
            />
            <span>{{ col.label }}</span>
          </label>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { Columns3 } from 'lucide-vue-next';
import type { ColumnDefinition } from '../../composables/useColumnPreferences';

defineProps<{
  toggleable: ColumnDefinition[];
  hiddenCount: number;
  isVisible: (key: string) => boolean;
  toggle: (key: string) => void;
  reset: () => void;
}>();

const emit = defineEmits<{ (e: 'reset'): void }>();

const open = ref(false);
const rootEl = ref<HTMLElement | null>(null);

function handleClickOutside(e: MouseEvent) {
  if (!open.value) return;
  const target = e.target as Node;
  if (rootEl.value && !rootEl.value.contains(target)) {
    open.value = false;
  }
}

function handleReset() {
  emit('reset');
}

onMounted(() => {
  rootEl.value = document.querySelector('.cp-wrap');
  document.addEventListener('click', handleClickOutside);
});
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside));
</script>

<style scoped>
.cp-wrap {
  position: relative;
  display: inline-block;
}
.cp-trigger {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.75rem;
  border: 1.5px solid rgba(60,50,35,0.12);
  border-radius: 9px;
  background: rgba(255,253,247,0.92);
  color: rgba(42,36,30,0.70);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.cp-trigger:hover { background: rgba(255,253,247,0.55); }
.cp-trigger[aria-expanded="true"] {
  border-color: #5a8aab;
  background: rgba(90,138,171,0.10);
  color: #3e6080;
}
.cp-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9999px;
  background: #3e6080;
  color: #fff;
  font-size: 0.7rem;
  font-weight: 600;
}
.cp-label {
  display: none;
}
@media (min-width: 640px) {
  .cp-label { display: inline; }
}
.cp-panel {
  position: absolute;
  z-index: 40;
  top: calc(100% + 6px);
  right: 0;
  min-width: 220px;
  background: rgba(255,253,247,0.92);
  border: 1px solid rgba(60,50,35,0.12);
  border-radius: 10px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
  padding: 6px 0;
}
.cp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 6px;
  border-bottom: 1px solid rgba(42,36,30,0.05);
  margin-bottom: 4px;
}
.cp-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(42,36,30,0.70);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.cp-reset {
  background: none;
  border: none;
  color: #4a7593;
  font-size: 0.75rem;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.cp-reset:hover { background: rgba(90,138,171,0.10); }
.cp-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 2px 0;
}
.cp-item { list-style: none; }
.cp-check {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 6px 12px;
  font-size: 0.875rem;
  color: #2a241e;
  cursor: pointer;
}
.cp-check:hover { background: rgba(255,253,247,0.55); }
.cp-check input { cursor: pointer; }
</style>
