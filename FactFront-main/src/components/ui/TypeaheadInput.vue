<template>
  <div class="ta-wrap" @keydown="onKeydown">
    <input
      ref="inputEl"
      :value="modelValue"
      :placeholder="placeholder"
      :class="inputClass"
      :disabled="disabled"
      type="text"
      autocomplete="off"
      role="combobox"
      :aria-expanded="open"
      aria-autocomplete="list"
      :aria-controls="listId"
      :aria-activedescendant="activeId"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
    />
    <ul
      v-if="open && filtered.length"
      :id="listId"
      class="ta-list"
      role="listbox"
    >
      <li
        v-for="(item, i) in filtered"
        :key="i"
        :id="`${listId}-${i}`"
        :class="['ta-item', i === activeIndex && 'ta-item--active']"
        role="option"
        :aria-selected="i === activeIndex"
        @mousedown.prevent="select(i)"
        @mouseenter="activeIndex = i"
      >
        <span v-html="highlight(item)" />
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    suggestions: string[];
    placeholder?: string;
    disabled?: boolean;
    inputClass?: string | Record<string, boolean>;
    minChars?: number;
    maxResults?: number;
  }>(),
  { minChars: 1, maxResults: 8 },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'select', value: string): void;
  (e: 'blur'): void;
}>();

const inputEl = ref<HTMLInputElement | null>(null);
const open = ref(false);
const activeIndex = ref(-1);
const listId = `ta-${Math.random().toString(36).slice(2, 8)}`;
// Tracks the last typed value so filtering works even before the parent
// re-passes the updated modelValue prop (e.g. in controlled-input patterns).
const internalQuery = ref(props.modelValue ?? '');

function normalize(s: string): string {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

const filtered = computed(() => {
  const q = (internalQuery.value ?? props.modelValue ?? '').trim();
  if (q.length < props.minChars) return [];
  const n = normalize(q);
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of props.suggestions) {
    if (!raw) continue;
    const key = raw.trim();
    if (!key || seen.has(key)) continue;
    if (normalize(key).includes(n) && key !== props.modelValue) {
      seen.add(key);
      out.push(key);
      if (out.length >= props.maxResults) break;
    }
  }
  return out;
});

const activeId = computed(() =>
  activeIndex.value >= 0 ? `${listId}-${activeIndex.value}` : undefined,
);

function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value;
  internalQuery.value = v;
  emit('update:modelValue', v);
  open.value = true;
  activeIndex.value = -1;
}

function onFocus() {
  if (filtered.value.length) open.value = true;
}

let blurTimer: ReturnType<typeof setTimeout> | null = null;
function onBlur() {
  emit('blur');
  // Delay close so a mousedown on a list item can register first.
  blurTimer = setTimeout(() => {
    open.value = false;
    activeIndex.value = -1;
  }, 120);
}

onBeforeUnmount(() => {
  if (blurTimer) clearTimeout(blurTimer);
});

function select(i: number) {
  const value = filtered.value[i];
  if (!value) return;
  internalQuery.value = value;
  emit('update:modelValue', value);
  emit('select', value);
  open.value = false;
  activeIndex.value = -1;
  inputEl.value?.focus();
}

function onKeydown(e: KeyboardEvent) {
  if (!open.value && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
    open.value = true;
    return;
  }
  if (!open.value) return;
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault();
      activeIndex.value = (activeIndex.value + 1) % filtered.value.length;
      break;
    case 'ArrowUp':
      e.preventDefault();
      activeIndex.value =
        (activeIndex.value - 1 + filtered.value.length) % filtered.value.length;
      break;
    case 'Enter':
      if (activeIndex.value >= 0) {
        e.preventDefault();
        select(activeIndex.value);
      }
      break;
    case 'Escape':
      e.preventDefault();
      e.stopPropagation();
      open.value = false;
      activeIndex.value = -1;
      break;
    case 'Tab':
      open.value = false;
      break;
  }
}

function highlight(text: string): string {
  const q = (internalQuery.value ?? props.modelValue ?? '').trim();
  if (!q) return escape(text);
  const n = normalize(text);
  const needle = normalize(q);
  const idx = n.indexOf(needle);
  if (idx < 0) return escape(text);
  const before = text.slice(0, idx);
  const match = text.slice(idx, idx + q.length);
  const after = text.slice(idx + q.length);
  return `${escape(before)}<mark class="ta-mark">${escape(match)}</mark>${escape(after)}`;
}

function escape(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

defineExpose({
  focus: () => inputEl.value?.focus(),
});
</script>

<style scoped>
.ta-wrap {
  position: relative;
}
.ta-list {
  position: absolute;
  z-index: 40;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.1);
  max-height: 240px;
  overflow-y: auto;
  padding: 4px 0;
}
.ta-item {
  padding: 6px 10px;
  font-size: 0.875rem;
  color: #1e293b;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ta-item--active {
  background: #eff6ff;
  color: #1d4ed8;
}
:deep(.ta-mark) {
  background: transparent;
  color: inherit;
  font-weight: 700;
}
</style>
