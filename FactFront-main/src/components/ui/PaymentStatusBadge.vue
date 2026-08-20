<script setup lang="ts">
import type { PaymentStatus } from '../../types/invoice';

defineProps<{ status: PaymentStatus }>();

const TONES: Record<PaymentStatus, string> = {
  PAID:    'bg-emerald-50 text-emerald-700 border-emerald-200',
  PARTIAL: 'bg-[rgba(90,138,171,0.14)] text-tide-blue-deep border-[rgba(90,138,171,0.28)]',
  UNPAID:  'bg-[rgba(42,36,30,0.06)] text-tide-ink/65 border-[rgba(42,36,30,0.12)]',
  OVERDUE: 'bg-[rgba(181,99,88,0.14)] text-tide-rust-deep border-[rgba(181,99,88,0.28)]',
};

const LABELS: Record<PaymentStatus, string> = {
  PAID: 'Paid',
  PARTIAL: 'Partial',
  UNPAID: 'Unpaid',
  OVERDUE: 'Overdue',
};
</script>

<template>
  <span :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border', TONES[status]]">
    {{ LABELS[status] }}
  </span>
</template>
