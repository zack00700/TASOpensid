<template>
  <Modal
    :open="open"
    size="sm"
    :title="title"
    :dismissible="!loading"
    initial-focus="none"
    @update:open="(v) => emit('update:open', v)"
  >
    <template #header="{ titleId }">
      <div class="px-6 pt-6 text-center">
        <div :class="['mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full', tonePalette.iconBg]">
          <component :is="tonePalette.icon" data-tone-icon :class="['h-10 w-10', tonePalette.iconColor]" />
        </div>
        <h2 :id="titleId" class="text-lg font-semibold text-slate-900">{{ title }}</h2>
      </div>
    </template>

    <p class="text-slate-600 text-center">
      <slot />
    </p>

    <template #footer>
      <Button
        ref="cancelRef"
        data-cancel-button
        variant="secondary"
        block
        :disabled="loading"
        @click="emit('update:open', false)"
      >{{ cancelLabel ?? 'Cancel' }}</Button>
      <Button
        ref="confirmRef"
        data-confirm-button
        :variant="tonePalette.buttonVariant"
        block
        :loading="loading"
        :class="tonePalette.buttonOverride"
        @click="emit('confirm')"
      >{{ confirmLabel ?? tonePalette.defaultLabel }}</Button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { Trash2, AlertTriangle, Info, CheckCircle } from 'lucide-vue-next';
import Modal from './Modal.vue';
import Button from './Button.vue';

type Tone = 'danger' | 'warning' | 'info' | 'success';
type FocusTarget = 'confirm' | 'cancel';

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    tone?: Tone;
    confirmLabel?: string;
    cancelLabel?: string;
    loading?: boolean;
    initialFocus?: FocusTarget;
  }>(),
  { tone: 'info', loading: false },
);

const emit = defineEmits<{
  'update:open': [value: boolean];
  confirm: [];
}>();

const cancelRef = ref<{ $el: HTMLButtonElement } | null>(null);
const confirmRef = ref<{ $el: HTMLButtonElement } | null>(null);

const tonePalette = computed(() => {
  switch (props.tone) {
    case 'danger':
      return {
        icon: Trash2,
        iconBg: 'bg-red-100',
        iconColor: 'text-red-600',
        buttonVariant: 'danger' as const,
        buttonOverride: '',
        defaultLabel: 'Delete',
      };
    case 'warning':
      return {
        icon: AlertTriangle,
        iconBg: 'bg-amber-100',
        iconColor: 'text-amber-600',
        buttonVariant: 'primary' as const,
        buttonOverride: '!bg-amber-600 hover:!bg-amber-700 focus-visible:!ring-amber-500',
        defaultLabel: 'Confirm',
      };
    case 'success':
      return {
        icon: CheckCircle,
        iconBg: 'bg-emerald-100',
        iconColor: 'text-emerald-600',
        buttonVariant: 'primary' as const,
        buttonOverride: '!bg-emerald-600 hover:!bg-emerald-700 focus-visible:!ring-emerald-500',
        defaultLabel: 'Finalize',
      };
    case 'info':
    default:
      return {
        icon: Info,
        iconBg: 'bg-blue-100',
        iconColor: 'text-blue-600',
        buttonVariant: 'primary' as const,
        buttonOverride: '',
        defaultLabel: 'OK',
      };
  }
});

const initialTarget = computed<FocusTarget>(() => {
  if (props.initialFocus) return props.initialFocus;
  return props.tone === 'danger' || props.tone === 'warning' ? 'cancel' : 'confirm';
});

watch(
  () => props.open,
  async (next) => {
    if (!next) return;
    await nextTick();
    await nextTick();
    const target = initialTarget.value === 'cancel' ? cancelRef.value : confirmRef.value;
    target?.$el?.focus();
  },
  { immediate: true },
);
</script>
