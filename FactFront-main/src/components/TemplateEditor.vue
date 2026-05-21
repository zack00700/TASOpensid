<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';
import { useEditor, EditorContent } from '@tiptap/vue-3';
import StarterKit from '@tiptap/starter-kit';
import TextAlign from '@tiptap/extension-text-align';
import TextStyle from '@tiptap/extension-text-style';
import { Color } from '@tiptap/extension-color';
import Table from '@tiptap/extension-table';
import TableRow from '@tiptap/extension-table-row';
import TableCell from '@tiptap/extension-table-cell';
import TableHeader from '@tiptap/extension-table-header';
import templateService from '../services/invoiceTemplateService';
import {
  Bold,
  Italic,
  AlignLeft,
  AlignCenter,
  AlignRight,
  AlignJustify,
  Image,
  TableIcon,
} from 'lucide-vue-next';

const { t } = useI18n();

// ---------------------------------------------------------------------------
// Props & emits
// ---------------------------------------------------------------------------
const props = defineProps<{
  templateId: string;
  initialHtml: string;
  initialCss: string;
}>();

const emit = defineEmits<{
  (e: 'saved'): void;
  (e: 'created', id: string): void;
}>();

// ---------------------------------------------------------------------------
// Template variables (Handlebars tokens used in the template service)
// ---------------------------------------------------------------------------
const TEMPLATE_VARIABLES = [
  {
    group: 'Factura',
    vars: [
      { label: 'Número factura', token: '{{invoiceNumber}}' },
      { label: 'Número borrador', token: '{{draftNumber}}' },
      { label: 'Fecha emisión', token: '{{issueDate}}' },
      { label: 'Moneda', token: '{{currency}}' },
      { label: 'Instalación', token: '{{facility}}' },
    ],
  },
  {
    group: 'Cliente',
    vars: [{ label: 'Nombre cliente', token: '{{customerName}}' }],
  },
  {
    group: 'Totales',
    vars: [
      { label: 'Subtotal', token: '{{subtotal}}' },
      { label: 'Total impuestos', token: '{{totalTax}}' },
      { label: 'Total general', token: '{{grandTotal}}' },
    ],
  },
  {
    group: 'Líneas (tabla)',
    vars: [
      {
        label: 'Bloque de líneas',
        token:
          '{{#each lines}}\n<tr><td>{{description}}</td><td>{{quantity}}</td><td>{{unitPrice}}</td><td>{{amount}}</td></tr>\n{{/each}}',
      },
      { label: 'Descripción línea', token: '{{description}}' },
      { label: 'Cantidad', token: '{{quantity}}' },
      { label: 'Precio unitario', token: '{{unitPrice}}' },
      { label: 'Importe', token: '{{amount}}' },
    ],
  },
] as const;

// ---------------------------------------------------------------------------
// Sample data for live preview
// ---------------------------------------------------------------------------
const SAMPLE_DATA: Record<string, any> = {
  invoiceNumber: 'INV-2025-001',
  draftNumber: 'DFT-2025-001',
  issueDate: '2025-01-15',
  currency: 'EUR',
  facility: 'PORT-A',
  customerName: 'ACME Shipping Co.',
  subtotal: '1 500,00',
  totalTax: '150,00',
  grandTotal: '1 650,00',
  lines: [
    { description: 'Container storage 20ft', quantity: '5', unitPrice: '100,00 €', amount: '500,00 €' },
    { description: 'Handling fee', quantity: '2', unitPrice: '250,00 €', amount: '500,00 €' },
  ],
};

// ---------------------------------------------------------------------------
// Mode: 'visual' | 'code'
// ---------------------------------------------------------------------------
const editorMode = ref<'visual' | 'code'>('visual');

// Inside Code mode: which sub-tab is active
const activeTab = ref<'html' | 'css'>('html');

// Whether the CSS modal/panel is open in visual mode
const showCssPanel = ref(false);

// ---------------------------------------------------------------------------
// Editor state
// ---------------------------------------------------------------------------
const htmlContent = ref(props.initialHtml || '');
const cssContent = ref(props.initialCss || '');

watch(() => props.initialHtml, (v) => {
  htmlContent.value = v || '';
  if (editor.value) {
    editor.value.commands.setContent(htmlContent.value || '', false);
  }
});
watch(() => props.initialCss, (v) => { cssContent.value = v || ''; });

// Textarea refs for cursor-aware insertion (Code mode)
const htmlTextarea = ref<HTMLTextAreaElement | null>(null);
const cssTextarea = ref<HTMLTextAreaElement | null>(null);

// Color picker current value
const currentTextColor = ref('#000000');

// ---------------------------------------------------------------------------
// TipTap editor (Visual mode)
// ---------------------------------------------------------------------------
const editor = useEditor({
  content: htmlContent.value || '',
  extensions: [
    StarterKit,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    TextStyle,
    Color,
    Table.configure({ resizable: true }),
    TableRow,
    TableCell,
    TableHeader,
  ],
  onUpdate({ editor: e }) {
    // Keep htmlContent in sync as user types in visual mode
    htmlContent.value = e.getHTML();
  },
});

onBeforeUnmount(() => {
  editor.value?.destroy();
});

// ---------------------------------------------------------------------------
// Mode switching
// ---------------------------------------------------------------------------
function switchToVisual() {
  if (editorMode.value === 'visual') return;
  // Sync code → visual
  if (editor.value) {
    editor.value.commands.setContent(htmlContent.value || '', false);
  }
  editorMode.value = 'visual';
}

function switchToCode() {
  if (editorMode.value === 'code') return;
  // Sync visual → code
  if (editor.value) {
    htmlContent.value = editor.value.getHTML();
  }
  editorMode.value = 'code';
  activeTab.value = 'html';
}

// ---------------------------------------------------------------------------
// Variable insertion
// ---------------------------------------------------------------------------
function insertVariable(token: string) {
  if (editorMode.value === 'visual') {
    // Insert into TipTap
    editor.value?.chain().focus().insertContent(token).run();
    return;
  }

  const isHtml = activeTab.value === 'html';
  const ta = isHtml ? htmlTextarea.value : cssTextarea.value;
  const content = isHtml ? htmlContent : cssContent;

  if (!ta) {
    content.value += token;
    return;
  }

  const start = ta.selectionStart ?? content.value.length;
  const end = ta.selectionEnd ?? content.value.length;
  content.value = content.value.slice(0, start) + token + content.value.slice(end);

  nextTick(() => {
    ta.selectionStart = ta.selectionEnd = start + token.length;
    ta.focus();
  });
}

// ---------------------------------------------------------------------------
// Toolbar actions (Visual mode)
// ---------------------------------------------------------------------------
function toolbarInsertImage() {
  const url = prompt(t('templateEditor.dialog.imageUrl'));
  if (url) {
    editor.value
      ?.chain()
      .focus()
      .insertContent(`<img src="${url}" style="max-width:100%"/>`)
      .run();
  }
}

function toolbarInsertTable() {
  editor.value
    ?.chain()
    .focus()
    .insertTable({ rows: 3, cols: 3, withHeaderRow: true })
    .run();
}

function onColorChange(event: Event) {
  const color = (event.target as HTMLInputElement).value;
  currentTextColor.value = color;
  editor.value?.chain().focus().setColor(color).run();
}

// ---------------------------------------------------------------------------
// Live preview — builds a full HTML document injected via srcdoc
// ---------------------------------------------------------------------------
const previewDoc = computed(() => buildPreviewDoc(htmlContent.value, cssContent.value));

function buildPreviewDoc(rawHtml: string, css: string): string {
  let html = rawHtml;

  // Replace {{#each lines}} … {{/each}} blocks
  html = html.replace(
    /\{\{#each lines\}\}([\s\S]*?)\{\{\/each\}\}/g,
    (_match, inner) =>
      SAMPLE_DATA.lines
        .map((line: Record<string, string>) => {
          let row = inner;
          for (const [k, v] of Object.entries(line)) {
            row = row.split(`{{${k}}}`).join(v);
          }
          return row;
        })
        .join(''),
  );

  // Replace simple scalar tokens
  for (const [k, v] of Object.entries(SAMPLE_DATA)) {
    if (typeof v === 'string') {
      html = html.split(`{{${k}}}`).join(v);
    }
  }

  return `<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"/><style>${css}</style></head><body>${html}</body></html>`;
}

// ---------------------------------------------------------------------------
// Save
// ---------------------------------------------------------------------------
const isSaving = ref(false);
const saveMsg = ref<{ type: 'success' | 'error'; text: string } | null>(null);

// Template type — set at creation only (immutable on edit per backend contract).
const templateType = ref<'draft' | 'final'>('final');
const isCreating = computed(() => !props.templateId);

async function doSave() {
  // Flush visual editor content before saving
  if (editorMode.value === 'visual' && editor.value) {
    htmlContent.value = editor.value.getHTML();
  }

  isSaving.value = true;
  saveMsg.value = null;
  try {
    const basePayload: any = { name: 'Invoice Template', html: htmlContent.value, css: cssContent.value };

    if (!props.templateId) {
      const created = await templateService.createTemplate({ ...basePayload, type: templateType.value });
      saveMsg.value = { type: 'success', text: t('templateEditor.toast.created') };
      emit('created', created.id!);
    } else {
      await templateService.updateTemplate(props.templateId, basePayload);
      saveMsg.value = { type: 'success', text: t('templateEditor.toast.saved') };
      emit('saved');
    }
  } catch (err: any) {
    saveMsg.value = { type: 'error', text: err?.message || t('templateEditor.toast.saveError') };
  } finally {
    isSaving.value = false;
    setTimeout(() => { saveMsg.value = null; }, 3500);
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 bg-gray-50 overflow-hidden">

    <!-- =================================================================
         LEFT PANEL
    ================================================================== -->
    <div class="flex flex-col w-1/2 min-w-0 border-r border-gray-200 bg-white">

      <!-- Mode toggle tabs -->
      <div class="flex items-center gap-0 border-b border-gray-200 bg-gray-50 px-2 pt-2">
        <button
          class="px-4 py-1.5 text-sm font-medium rounded-t border border-b-0 transition-colors"
          :class="editorMode === 'visual'
            ? 'bg-white border-gray-200 text-gray-900 z-10'
            : 'bg-transparent border-transparent text-gray-500 hover:text-gray-700'"
          @click="switchToVisual"
        >
          {{ t('templateEditor.tab.visual') }}
        </button>
        <button
          class="px-4 py-1.5 text-sm font-medium rounded-t border border-b-0 transition-colors ml-1"
          :class="editorMode === 'code'
            ? 'bg-white border-gray-200 text-gray-900 z-10'
            : 'bg-transparent border-transparent text-gray-500 hover:text-gray-700'"
          @click="switchToCode"
        >
          {{ t('templateEditor.tab.code') }}
        </button>

        <!-- Inside Code mode: HTML / CSS sub-tabs -->
        <template v-if="editorMode === 'code'">
          <div class="w-px h-5 bg-gray-300 mx-2"></div>
          <button
            class="px-3 py-1.5 text-sm font-medium rounded-t border border-b-0 transition-colors"
            :class="activeTab === 'html'
              ? 'bg-white border-gray-200 text-gray-900 z-10'
              : 'bg-transparent border-transparent text-gray-500 hover:text-gray-700'"
            @click="activeTab = 'html'"
          >
            {{ t('templateEditor.tab.html') }}
          </button>
          <button
            class="px-3 py-1.5 text-sm font-medium rounded-t border border-b-0 transition-colors ml-1"
            :class="activeTab === 'css'
              ? 'bg-white border-gray-200 text-gray-900 z-10'
              : 'bg-transparent border-transparent text-gray-500 hover:text-gray-700'"
            @click="activeTab = 'css'"
          >
            {{ t('templateEditor.tab.css') }}
          </button>
        </template>

        <span class="ml-auto text-xs text-gray-400 pr-2">{{ t('templateEditor.hint.insertClick') }}</span>
      </div>

      <!-- ---------------------------------------------------------------
           VISUAL MODE
      --------------------------------------------------------------- -->
      <template v-if="editorMode === 'visual'">

        <!-- Formatting toolbar -->
        <div class="flex flex-wrap items-center gap-0.5 px-2 py-1.5 border-b border-gray-200 bg-gray-50 flex-shrink-0">

          <!-- Bold -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive('bold') ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Bold"
            @click="editor?.chain().focus().toggleBold().run()"
          >
            <Bold :size="14" />
          </button>

          <!-- Italic -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive('italic') ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Italic"
            @click="editor?.chain().focus().toggleItalic().run()"
          >
            <Italic :size="14" />
          </button>

          <div class="w-px h-5 bg-gray-300 mx-1"></div>

          <!-- H1 -->
          <button
            class="px-2 py-1 rounded text-xs font-bold transition-colors hover:bg-gray-200"
            :class="editor?.isActive('heading', { level: 1 }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Heading 1"
            @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
          >H1</button>

          <!-- H2 -->
          <button
            class="px-2 py-1 rounded text-xs font-bold transition-colors hover:bg-gray-200"
            :class="editor?.isActive('heading', { level: 2 }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Heading 2"
            @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()"
          >H2</button>

          <!-- H3 -->
          <button
            class="px-2 py-1 rounded text-xs font-bold transition-colors hover:bg-gray-200"
            :class="editor?.isActive('heading', { level: 3 }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Heading 3"
            @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()"
          >H3</button>

          <div class="w-px h-5 bg-gray-300 mx-1"></div>

          <!-- Align Left -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive({ textAlign: 'left' }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Align left"
            @click="editor?.chain().focus().setTextAlign('left').run()"
          >
            <AlignLeft :size="14" />
          </button>

          <!-- Align Center -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive({ textAlign: 'center' }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Align center"
            @click="editor?.chain().focus().setTextAlign('center').run()"
          >
            <AlignCenter :size="14" />
          </button>

          <!-- Align Right -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive({ textAlign: 'right' }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Align right"
            @click="editor?.chain().focus().setTextAlign('right').run()"
          >
            <AlignRight :size="14" />
          </button>

          <!-- Align Justify -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200"
            :class="editor?.isActive({ textAlign: 'justify' }) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'"
            title="Justify"
            @click="editor?.chain().focus().setTextAlign('justify').run()"
          >
            <AlignJustify :size="14" />
          </button>

          <div class="w-px h-5 bg-gray-300 mx-1"></div>

          <!-- Text color -->
          <label
            class="p-1.5 rounded transition-colors hover:bg-gray-200 text-gray-600 cursor-pointer flex items-center gap-1"
            title="Text color"
          >
            <span class="text-xs font-bold" :style="{ color: currentTextColor }">A</span>
            <div
              class="w-4 h-2 rounded-sm border border-gray-300"
              :style="{ backgroundColor: currentTextColor }"
            ></div>
            <input
              type="color"
              :value="currentTextColor"
              class="sr-only"
              @input="onColorChange"
            />
          </label>

          <div class="w-px h-5 bg-gray-300 mx-1"></div>

          <!-- Insert image -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200 text-gray-600"
            title="Insert image"
            @click="toolbarInsertImage"
          >
            <Image :size="14" />
          </button>

          <!-- Insert table -->
          <button
            class="p-1.5 rounded transition-colors hover:bg-gray-200 text-gray-600"
            title="Insert 3×3 table"
            @click="toolbarInsertTable"
          >
            <TableIcon :size="14" />
          </button>
        </div>

        <!-- TipTap editor area -->
        <div class="flex-1 overflow-auto min-h-0">
          <EditorContent
            :editor="editor"
            class="h-full [&_.ProseMirror]:min-h-full [&_.ProseMirror]:p-4 [&_.ProseMirror]:focus:outline-none [&_.ProseMirror]:text-sm [&_.ProseMirror_table]:border-collapse [&_.ProseMirror_table]:w-full [&_.ProseMirror_td]:border [&_.ProseMirror_td]:border-gray-300 [&_.ProseMirror_td]:p-1.5 [&_.ProseMirror_th]:border [&_.ProseMirror_th]:border-gray-300 [&_.ProseMirror_th]:p-1.5 [&_.ProseMirror_th]:bg-gray-100 [&_.ProseMirror_h1]:text-2xl [&_.ProseMirror_h1]:font-bold [&_.ProseMirror_h1]:mb-2 [&_.ProseMirror_h2]:text-xl [&_.ProseMirror_h2]:font-bold [&_.ProseMirror_h2]:mb-2 [&_.ProseMirror_h3]:text-lg [&_.ProseMirror_h3]:font-bold [&_.ProseMirror_h3]:mb-1 [&_.ProseMirror_ul]:list-disc [&_.ProseMirror_ul]:pl-5 [&_.ProseMirror_ol]:list-decimal [&_.ProseMirror_ol]:pl-5 [&_.ProseMirror_img]:max-w-full"
          />
        </div>

        <!-- Edit CSS button (bottom of visual panel) -->
        <div class="flex items-center gap-3 px-3 py-2 border-t border-gray-200 bg-gray-50 flex-shrink-0">
          <!-- Type selector (visible on creation only) -->
          <label v-if="isCreating" class="flex items-center gap-2 text-sm text-slate-700">
            {{ t('templateEditor.label.type') }}
            <select v-model="templateType" class="border border-slate-300 rounded px-2 py-1 text-sm bg-white">
              <option value="final">Final</option>
              <option value="draft">Draft</option>
            </select>
          </label>

          <button
            class="px-4 py-1.5 rounded bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium disabled:opacity-60 transition-colors"
            :disabled="isSaving"
            @click="doSave"
          >
            {{ isSaving ? t('templateEditor.button.saving') : t('templateEditor.button.save') }}
          </button>

          <button
            class="px-3 py-1.5 rounded border border-gray-300 hover:bg-gray-100 text-gray-600 text-sm transition-colors"
            @click="showCssPanel = !showCssPanel"
          >
            {{ showCssPanel ? t('templateEditor.button.closeCss') : t('templateEditor.button.editCss') }}
          </button>

          <span
            v-if="saveMsg"
            class="text-sm"
            :class="saveMsg.type === 'success' ? 'text-green-600' : 'text-red-600'"
          >
            {{ saveMsg.text }}
          </span>
        </div>

        <!-- Inline CSS panel (collapsible, shown at bottom in visual mode) -->
        <div
          v-if="showCssPanel"
          class="flex-shrink-0 border-t border-gray-200 flex flex-col"
          style="height: 180px;"
        >
          <div class="flex items-center justify-between px-3 py-1 bg-gray-50 border-b border-gray-200">
            <span class="text-xs font-semibold text-gray-500 uppercase tracking-wide">{{ t('templateEditor.tab.css') }}</span>
            <button class="text-xs text-gray-400 hover:text-gray-600" @click="showCssPanel = false">✕</button>
          </div>
          <textarea
            v-model="cssContent"
            class="flex-1 w-full font-mono text-xs p-3 resize-none focus:outline-none bg-white leading-relaxed"
            placeholder="/* body { font-family: Arial; } */"
            spellcheck="false"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
          />
        </div>

      </template>

      <!-- ---------------------------------------------------------------
           CODE MODE
      --------------------------------------------------------------- -->
      <template v-else>

        <!-- HTML textarea -->
        <textarea
          v-show="activeTab === 'html'"
          ref="htmlTextarea"
          v-model="htmlContent"
          class="flex-1 w-full font-mono text-xs p-3 resize-none focus:outline-none bg-white leading-relaxed"
          placeholder="<h1>{{invoiceNumber}}</h1>
<table>
  {{#each lines}}
  <tr><td>{{description}}</td><td>{{amount}}</td></tr>
  {{/each}}
</table>"
          spellcheck="false"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
        />

        <!-- CSS textarea -->
        <textarea
          v-show="activeTab === 'css'"
          ref="cssTextarea"
          v-model="cssContent"
          class="flex-1 w-full font-mono text-xs p-3 resize-none focus:outline-none bg-white leading-relaxed"
          placeholder="/* body { font-family: Arial; }
.invoice-title { color: #af4e8a; }
table { border-collapse: collapse; width: 100%; } */"
          spellcheck="false"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
        />

        <!-- Action bar -->
        <div class="flex items-center gap-3 px-3 py-2 border-t border-gray-200 bg-gray-50 flex-shrink-0">
          <button
            class="px-4 py-1.5 rounded bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium disabled:opacity-60 transition-colors"
            :disabled="isSaving"
            @click="doSave"
          >
            {{ isSaving ? t('templateEditor.button.saving') : t('templateEditor.button.save') }}
          </button>

          <span
            v-if="saveMsg"
            class="text-sm"
            :class="saveMsg.type === 'success' ? 'text-green-600' : 'text-red-600'"
          >
            {{ saveMsg.text }}
          </span>
        </div>
      </template>

    </div>

    <!-- =================================================================
         RIGHT PANEL — Live preview + Variables sidebar
    ================================================================== -->
    <div class="flex flex-col flex-1 min-w-0">

      <!-- Preview header -->
      <div class="flex items-center justify-between px-3 py-2 border-b border-gray-200 bg-gray-50 flex-shrink-0">
        <span class="text-xs font-semibold text-gray-500 uppercase tracking-wide">{{ t('templateEditor.label.livePreview') }}</span>
        <span class="text-xs text-gray-400">{{ t('templateEditor.label.sampleData') }}</span>
      </div>

      <!-- Split: preview iframe + variables sidebar -->
      <div class="flex flex-1 min-h-0 overflow-hidden">

        <!-- Preview iframe -->
        <iframe
          :srcdoc="previewDoc"
          class="flex-1 border-0 bg-white"
          sandbox="allow-same-origin"
          title="Template preview"
        />

        <!-- Variables sidebar -->
        <aside class="w-52 flex-shrink-0 overflow-y-auto border-l border-gray-200 bg-white px-3 py-3 text-sm">
          <p class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">{{ t('templateEditor.label.variables') }}</p>

          <div v-for="group in TEMPLATE_VARIABLES" :key="group.group" class="mb-4">
            <p class="text-xs font-medium text-gray-400 mb-1">{{ group.group }}</p>
            <ul class="space-y-1">
              <li
                v-for="v in group.vars"
                :key="v.token"
                class="flex items-start gap-1"
              >
                <button
                  class="flex-shrink-0 w-5 h-5 rounded bg-blue-50 hover:bg-blue-100 text-blue-600 text-xs font-bold leading-none flex items-center justify-center mt-0.5"
                  :title="`Insertar: ${v.token}`"
                  @click="insertVariable(v.token)"
                >+</button>
                <span class="text-xs text-gray-600 leading-tight" :title="v.token">{{ v.label }}</span>
              </li>
            </ul>
          </div>
        </aside>
      </div>
    </div>

  </div>
</template>

<style scoped>
textarea {
  tab-size: 2;
}
</style>
