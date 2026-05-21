<template>
  <div class="bg-gradient-to-br from-slate-50 to-blue-50 border border-blue-100 rounded-xl p-4 space-y-4">
    <div class="flex items-start gap-2">
      <Bot class="w-4 h-4 text-indigo-500 flex-shrink-0 mt-1" />
      <div class="flex-1">
        <h4 v-if="data.title" class="text-sm font-semibold text-gray-900">{{ data.title }}</h4>
        <span
          v-if="data.category"
          class="inline-block mt-1 text-xs font-medium text-indigo-700 bg-indigo-100 px-2 py-0.5 rounded"
        >
          {{ data.category }}
        </span>
      </div>
    </div>

    <div v-if="data.businessPain || data.actor || data.frequency" class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
      <div v-if="data.actor">
        <p class="font-semibold text-gray-500 uppercase tracking-wide mb-0.5">{{ t('featureRequestSummaryCard.role') }}</p>
        <p class="text-gray-800">{{ data.actor }}</p>
      </div>
      <div v-if="data.frequency">
        <p class="font-semibold text-gray-500 uppercase tracking-wide mb-0.5">{{ t('featureRequestSummaryCard.frequency') }}</p>
        <p class="text-gray-800">{{ data.frequency }}</p>
      </div>
      <div v-if="data.trigger?.event" class="sm:col-span-1">
        <p class="font-semibold text-gray-500 uppercase tracking-wide mb-0.5">{{ t('featureRequestSummaryCard.trigger') }}</p>
        <p class="text-gray-800">
          {{ data.trigger.event }}<span v-if="data.trigger.source"> · {{ data.trigger.source }}</span>
        </p>
      </div>
    </div>

    <div v-if="data.businessPain">
      <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">{{ t('featureRequestSummaryCard.painPoint') }}</p>
      <p class="text-sm text-gray-800 leading-relaxed">{{ data.businessPain }}</p>
    </div>

    <div v-if="data.formula">
      <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1 flex items-center gap-1">
        <Calculator class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.formula') }}
      </p>
      <pre class="text-xs bg-white border border-gray-200 rounded-md p-2.5 overflow-x-auto whitespace-pre-wrap text-gray-800">{{ data.formula }}</pre>
    </div>

    <div v-if="data.inputs?.length" class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1">
          <ArrowDownToLine class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.inputs') }}
        </p>
        <ul class="space-y-1 text-sm">
          <li v-for="(input, i) in data.inputs" :key="`in-${i}`" class="text-gray-800">
            <span class="font-medium">{{ input.name }}</span>
            <span v-if="input.type" class="text-gray-500"> ({{ input.type }})</span>
            <span v-if="input.source" class="text-gray-500"> · {{ input.source }}</span>
          </li>
        </ul>
      </div>
      <div v-if="data.outputs?.length">
        <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1">
          <ArrowUpFromLine class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.outputs') }}
        </p>
        <ul class="space-y-1 text-sm">
          <li v-for="(output, i) in data.outputs" :key="`out-${i}`" class="text-gray-800">
            <span class="font-medium">{{ output.name }}</span>
            <span v-if="output.description" class="text-gray-600"> — {{ output.description }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div v-if="data.edgeCases?.length">
      <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1">
        <AlertTriangle class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.edgeCases') }}
      </p>
      <ul class="space-y-1 text-sm">
        <li v-for="(edge, i) in data.edgeCases" :key="`ec-${i}`" class="text-gray-800">
          <span class="font-medium">{{ edge.case }}</span>
          <span v-if="edge.handling" class="text-gray-600"> → {{ edge.handling }}</span>
        </li>
      </ul>
    </div>

    <div v-if="data.acceptanceCriteria?.length">
      <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1">
        <CheckCircle2 class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.acceptanceCriteria') }}
      </p>
      <ul class="space-y-1 text-sm list-disc list-inside text-gray-800">
        <li v-for="(ac, i) in data.acceptanceCriteria" :key="`ac-${i}`">{{ ac }}</li>
      </ul>
    </div>

    <div v-if="data.integrations?.length">
      <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 flex items-center gap-1">
        <Plug class="w-3.5 h-3.5" /> {{ t('featureRequestSummaryCard.integrations') }}
      </p>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="(i, idx) in data.integrations"
          :key="`int-${idx}`"
          class="text-xs text-gray-700 bg-white border border-gray-200 rounded px-2 py-0.5"
        >
          {{ i }}
        </span>
      </div>
    </div>

    <div v-if="data.assumptions?.length || data.openQuestions?.length" class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2 border-t border-blue-100">
      <div v-if="data.assumptions?.length">
        <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">{{ t('featureRequestSummaryCard.assumptions') }}</p>
        <ul class="space-y-1 text-xs list-disc list-inside text-gray-700">
          <li v-for="(a, i) in data.assumptions" :key="`as-${i}`">{{ a }}</li>
        </ul>
      </div>
      <div v-if="data.openQuestions?.length">
        <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5 text-amber-700">{{ t('featureRequestSummaryCard.openQuestions') }}</p>
        <ul class="space-y-1 text-xs list-disc list-inside text-amber-800">
          <li v-for="(q, i) in data.openQuestions" :key="`oq-${i}`">{{ q }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { Bot, Calculator, ArrowDownToLine, ArrowUpFromLine, AlertTriangle, CheckCircle2, Plug } from 'lucide-vue-next';
import type { StructuredSummaryData } from '@/types/featureRequest';

const { t } = useI18n();

defineProps<{ data: StructuredSummaryData }>();
</script>
