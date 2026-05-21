<template>
  <Teleport to="body">
    <Transition
      :enter-active-class="motionEnter"
      :enter-from-class="enterFrom"
      :enter-to-class="enterTo"
      :leave-active-class="motionLeave"
      :leave-from-class="enterTo"
      :leave-to-class="enterFrom"
      @after-leave="$emit('close')"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <div
          data-modal-scrim
          class="absolute inset-0 bg-slate-900/50"
          @click="onScrimClick"
        />
        <div
          ref="panelRef"
          data-modal-panel
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          tabindex="-1"
          :class="['relative bg-white rounded-xl shadow-xl w-full', sizeClass]"
        >
          <slot name="header" :title-id="titleId">
            <div v-if="title" class="px-6 pt-6">
              <h2 :id="titleId" class="text-lg font-semibold text-slate-900">{{ title }}</h2>
            </div>
          </slot>
          <div class="px-6 py-4">
            <slot />
          </div>
          <div v-if="$slots.footer" class="px-6 pb-6 pt-2 flex justify-end gap-3">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import { useEscapeToClose } from '../../composables/useKeyboardShortcut';
import { useFocusTrap } from '../../composables/useFocusTrap';
import { useScrollLock } from '../../composables/useScrollLock';

type Size = 'sm' | 'md' | 'lg' | 'xl';

const props = withDefaults(
  defineProps<{
    open: boolean;
    size?: Size;
    title?: string;
    dismissible?: boolean;
    initialFocus?: 'auto' | 'none';
  }>(),
  {
    size: 'md',
    dismissible: true,
    initialFocus: 'auto',
  },
);

const emit = defineEmits<{
  'update:open': [value: boolean];
  close: [];
}>();

const panelRef = ref<HTMLElement | null>(null);
const titleId = `modal-title-${useId()}`;

const sizeClass = computed(
  () =>
    ({
      sm: 'max-w-md',
      md: 'max-w-2xl',
      lg: 'max-w-4xl',
      xl: 'max-w-6xl',
    }[props.size]),
);

const isOpenRef = computed(() => props.open);
useEscapeToClose(isOpenRef, () => {
  if (props.dismissible) emit('update:open', false);
});

useFocusTrap(panelRef, () => props.open);
useScrollLock(() => props.open);

function onScrimClick() {
  if (props.dismissible) emit('update:open', false);
}

const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;

const motionEnter = reduceMotion ? '' : 'transition duration-150 ease-out';
const motionLeave = reduceMotion ? '' : 'transition duration-100 ease-in';
const enterFrom = reduceMotion ? '' : 'opacity-0 scale-95';
const enterTo = reduceMotion ? '' : 'opacity-100 scale-100';
</script>
