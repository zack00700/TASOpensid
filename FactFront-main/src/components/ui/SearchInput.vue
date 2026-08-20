<template>
  <div class="relative" :class="block ? 'w-full' : 'w-64'">
    <Search class="h-5 w-5 text-tide-ink/40 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
    <input
      ref="inputEl"
      :value="modelValue"
      :placeholder="placeholder"
      :aria-label="ariaLabel"
      type="search"
      autocomplete="off"
      class="w-full pl-10 pr-9 py-2 border border-[rgba(60,50,35,0.14)] rounded-tide-btn text-sm bg-[rgba(255,253,247,0.75)] text-tide-ink placeholder:text-tide-ink/40 focus:outline-none focus:ring-1 focus:ring-tide-blue focus:border-tide-blue transition-colors"
      @input="onInput"
    />
    <button
      v-if="modelValue"
      type="button"
      aria-label="Clear search"
      class="absolute right-2 top-1/2 -translate-y-1/2 text-tide-ink/40 hover:text-tide-ink/70 p-1 rounded"
      @click="clear"
    >
      <X class="h-4 w-4" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue';
import { Search, X } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    placeholder?: string;
    ariaLabel?: string;
    block?: boolean;
    debounce?: number;
  }>(),
  {
    placeholder: 'Search…',
    ariaLabel: 'Search',
    debounce: 0,
  },
);

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

const inputEl = ref<HTMLInputElement | null>(null);
let timer: ReturnType<typeof setTimeout> | null = null;

function onInput(event: Event) {
  const v = (event.target as HTMLInputElement).value;
  if (props.debounce > 0) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => emit('update:modelValue', v), props.debounce);
  } else {
    emit('update:modelValue', v);
  }
}

function clear() {
  if (timer) clearTimeout(timer);
  emit('update:modelValue', '');
  inputEl.value?.focus();
}

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer);
});

defineExpose({
  focus: () => inputEl.value?.focus(),
});
</script>
