<script setup lang="ts">
import type { PaymentStatus } from '../../types/invoice';

defineProps<{ status: PaymentStatus }>();

const TONES: Record<PaymentStatus, string> = {
  PAID:    'bg-emerald-50 text-emerald-700 border-emerald-200',
  PARTIAL: 'bg-blue-50 text-blue-700 border-blue-200',
  UNPAID:  'bg-slate-100 text-slate-600 border-slate-200',
  OVERDUE: 'bg-red-50 text-red-700 border-red-200',
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
