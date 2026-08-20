<template>
  <div class="vessel-ac">
    <TypeaheadInput
      :model-value="modelValue"
      :suggestions="suggestions"
      :placeholder="placeholder"
      :disabled="disabled"
      :input-class="inputClass"
      @update:model-value="onInput"
      @select="onSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import TypeaheadInput from './TypeaheadInput.vue';
import { useVessel } from '../../composables/use.vessel';
import type { Vessel } from '../../types/vessel';

const props = defineProps<{
  modelValue: string;
  disabled?: boolean;
  placeholder?: string;
  inputClass?: string | Record<string, boolean>;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  // Fired when the user picks a known vessel from the dropdown. Parents
  // use this to auto-fill the vesselId and lock that input.
  (e: 'select', vessel: Vessel): void;
  // Fired when the user types a vessel name that doesn't match any known
  // vessel — parents may want to unlock vesselId so the operator can
  // enter one manually.
  (e: 'clear'): void;
}>();

const { vessels } = useVessel();

const suggestions = computed<string[]>(() =>
  (vessels.value ?? []).map((v) => v.name).filter(Boolean),
);

function findVesselByName(name: string): Vessel | undefined {
  const trimmed = name.trim();
  if (!trimmed) return undefined;
  return (vessels.value ?? []).find((v) => v.name === trimmed);
}

function onInput(v: string) {
  emit('update:modelValue', v);
  // If the typed value no longer matches a known vessel, signal the parent
  // so the previously locked vesselId can be released.
  if (!findVesselByName(v)) emit('clear');
}

function onSelect(v: string) {
  const match = findVesselByName(v);
  if (match) emit('select', match);
}

// Expose props.placeholder etc. — only here to silence unused warnings
// since defineProps already binds them in template.
void props;
</script>
