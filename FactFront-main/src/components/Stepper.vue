<template>
    <nav class="stepper" :aria-label="ariaLabel">
      <ol class="stepper-list" role="list">
        <li
          v-for="(step, i) in steps"
          :key="i"
          class="step"
          :class="{ completed: i < current, current: i === current, disabled: !isStepEnabled(i) }"
        >
          <!-- Clickable marker/label when allowed -->
          <button
            v-if="clickable && isStepEnabled(i)"
            type="button"
            class="step-hit"
            :aria-current="i === current ? 'step' : undefined"
            :aria-disabled="!isStepEnabled(i) || i === current"
            @click="onSelect(i)"
            @keyup.enter="onSelect(i)"
          >
            <span class="step-marker" aria-hidden="true">{{ i + 1 }}</span>
            <span class="step-label">
              <slot name="label" :step="normalized[i]" :index="i">
                {{ normalized[i].label }}
              </slot>
            </span>
          </button>
  
          <!-- Non-clickable -->
          <div
            v-else
            class="step-hit"
            :aria-current="i === current ? 'step' : undefined"
          >
            <span class="step-marker" aria-hidden="true">{{ i + 1 }}</span>
            <span class="step-label">
              <slot name="label" :step="normalized[i]" :index="i">
                {{ normalized[i].label }}
              </slot>
            </span>
          </div>
        </li>
      </ol>
    </nav>
  </template>
  
  <script setup lang="ts">
  import { computed } from 'vue';
  
  type StepItem = string | { label: string; disabled?: boolean };
  
  const props = withDefaults(defineProps<{
    steps: StepItem[];
    /** zero-based current step index */
    current: number;
    /** allow clicking previous (and optionally future) steps */
    clickable?: boolean;
    /** allow clicking future steps when clickable */
    allowFuture?: boolean;
    /** aria label */
    ariaLabel?: string;
  }>(), {
    clickable: true,
    allowFuture: false,
    ariaLabel: 'Progress'
  });
  
  const emit = defineEmits<{
    (e: 'update:current', value: number): void;
    (e: 'change', value: number): void;
  }>();
  
  const normalized = computed(() =>
    props.steps.map(s => typeof s === 'string' ? { label: s } : s)
  );
  
  function isStepEnabled(i: number) {
    const item = normalized.value[i];
    if (item?.disabled) return false;
    if (!props.clickable) return false;
    return i <= props.current || props.allowFuture;
  }
  
  function onSelect(i: number) {
    if (!isStepEnabled(i) || i === props.current) return;
    emit('update:current', i);
    emit('change', i);
  }
  </script>
  
  <style scoped>
  :root{
    --c-border:#e5e7eb;
    --c-muted:#6b7280;
    --c-active:#1f2937;
    --c-done:#10b981;
    --c-connector:#d1d5db;
    --c-connector-done:#86efac;
    --sz-marker: 32px;
    --gap: 12px;
  }
  
  /* Base */
  .stepper{ width:100%; }
  .stepper-list{
    list-style:none;
    margin:0;
    padding:0;
    display:grid;
    grid-auto-flow:column;
    grid-auto-columns:1fr;
    column-gap: clamp(12px, 2vw, 32px);
    align-items:start;
  }
  
  /* Step layout: marker on top, label below (prevents overlap) */
  .step{
    display:grid;
    grid-template-rows: var(--sz-marker) auto;
    grid-template-columns: 1fr;
    row-gap: var(--gap);
    text-align:center;
    position:relative;
    min-width:0;
  }
  
  /* Connector (horizontal) */
  .step::before{
    content:"";
    position:absolute;
    top: calc(var(--sz-marker)/2 - 1px);
    height:2px;
    background: var(--c-connector);
    left:-50%;
    right:50%;
    z-index:0;
  }
  .step:first-child::before{ display:none; }
  .step.completed::before{ background: var(--c-connector-done); }
  
  /* Click target */
  .step-hit{
    display: grid;
    grid-template-rows: var(--sz-marker) auto;
    grid-template-columns: 1fr;
    row-gap: var(--gap);
    background:none;
    border:0;
    padding:0;
    text-align:inherit;
    cursor: default;
  }
  .step:not(.disabled) .step-hit{ cursor: pointer; }
  .step:not(.disabled) .step-hit[aria-current="step"]{ cursor: default; }
  
  /* Marker */
  .step-marker{
    z-index:1;
    justify-self:center;
    width:var(--sz-marker);
    height:var(--sz-marker);
    border-radius:50%;
    display:grid;
    place-items:center;
    border:2px solid var(--c-border);
    background:white;
    font: 600 14px/1 system-ui, -apple-system, Segoe UI, Roboto, "Helvetica Neue", Arial, "Noto Sans";
    color: var(--c-muted);
  }
  
  /* States */
  .step.completed .step-marker{
    border-color: var(--c-done);
    color: var(--c-done);
  }
  .step.current .step-marker{
    border-color: var(--c-active);
    color: var(--c-active);
    box-shadow: 0 0 0 3px color-mix(in oklab, var(--c-active) 15%, transparent);
  }
  .step.disabled .step-marker{ opacity:.6; }
  
  /* Labels wrap (no overlap) */
  .step-label{
    display:block;
    font: 500 14px/1.3 system-ui, -apple-system, Segoe UI, Roboto, "Helvetica Neue", Arial;
    color: var(--c-active);
    word-break: break-word;
  }
  .step.completed .step-label{ color: var(--c-muted); }
  .step.disabled .step-label{ color: var(--c-muted); opacity:.85; }
  
  /* Focus ring */
  .step-hit:focus-visible{
    outline: 3px solid color-mix(in oklab, var(--c-active) 20%, transparent);
    outline-offset: 4px;
    border-radius: 6px;
  }
  
  /* Mobile: vertical stepper */
  @media (max-width: 640px){
    .stepper-list{
      grid-auto-flow:row;
      grid-auto-rows:auto;
      row-gap:16px;
    }
    .step{
      grid-template-columns: var(--sz-marker) 1fr;
      grid-template-rows: auto;
      column-gap: 12px;
      text-align:left;
      align-items:center;
    }
    .step::before{
      left: calc(var(--sz-marker)/2 - 1px);
      right: auto;
      top: -16px;
      bottom: -16px;
      width:2px;
      height:auto;
      background: var(--c-connector);
    }
    .step:first-child::before{ top: 50%; }
    .step:last-child::before{ bottom: 50%; }
    .step-marker{ justify-self:start; }
    .step-label{ align-self:center; }
    .step.completed::before{ background: var(--c-connector-done); }
  }
  </style>
  