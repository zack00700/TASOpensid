<script setup lang="ts">
import { ref, watch } from 'vue';
import TemplateList from './TemplateList.vue';
import TemplateEditor from './TemplateEditor.vue';
import templateService from '../services/invoiceTemplateService';

const activeId = ref<string | null>(null);
const initialHtml = ref('');
const initialCss = ref('');
const loadError = ref<string | null>(null);
const isLoading = ref(false);

async function openEditor(id?: string) {
  loadError.value = null;
  initialHtml.value = '';
  initialCss.value = '';

  if (id) {
    isLoading.value = true;
    try {
      const tpl = await templateService.getTemplate(id);
      initialHtml.value = tpl.template?.html ?? tpl.html ?? '';
      initialCss.value = tpl.template?.css ?? tpl.css ?? '';
    } catch (err: any) {
      loadError.value = err?.message || 'Error al cargar el template.';
      isLoading.value = false;
      return;
    } finally {
      isLoading.value = false;
    }
  }

  activeId.value = id ?? '';
}

function closeEditor() {
  activeId.value = null;
  initialHtml.value = '';
  initialCss.value = '';
}

// Re-fetch when activeId changes externally (edge case)
watch(activeId, (id) => {
  if (id === null) {
    initialHtml.value = '';
    initialCss.value = '';
  }
});
</script>

<template>
  <div class="h-full flex flex-col">
    <!-- Loading state -->
    <div v-if="isLoading" class="flex items-center justify-center h-full text-gray-500 text-sm">
      {{ $t('invoiceTemplateDesigner.loading') }}
    </div>

    <!-- Error state -->
    <div v-else-if="loadError" class="p-4 text-red-600 text-sm">
      {{ loadError }}
      <button class="ml-3 underline text-blue-600" @click="loadError = null; activeId = null">{{ $t('common.back') }}</button>
    </div>

    <!-- Template list -->
    <TemplateList
      v-else-if="activeId === null"
      @edit="openEditor"
    />

    <!-- Template editor -->
    <TemplateEditor
      v-else
      :template-id="activeId"
      :initial-html="initialHtml"
      :initial-css="initialCss"
      class="flex-1 min-h-0"
      @saved="closeEditor"
      @created="(id) => { activeId = id }"
    />
  </div>
</template>

<style scoped>
</style>
