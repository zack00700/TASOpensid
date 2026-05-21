<!-- To enable the floating button globally, add to App.vue:
     <FeatureRequestWizard />
     (place it just before the closing </template> tag, after the teleported FABs) -->

<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Lightbulb, Bot, Send, X, CheckCircle, Loader2 } from 'lucide-vue-next';
import {
  createFeatureRequest,
  sendChatMessage,
} from '../services/featureRequestService';
import type { FeatureRequest, ConversationMessage, TicketCategory } from '../types/featureRequest';

const { t } = useI18n();

// ── State ──────────────────────────────────────────────────────────────────

const isOpen = ref(false);
const isSending = ref(false);
const activeRequest = ref<FeatureRequest | null>(null);
const chatContainerRef = ref<HTMLElement | null>(null);

// Step 1 form
const newTitle = ref('');
const newDescription = ref('');
const newCategory = ref<TicketCategory | ''>('');
const isCreating = ref(false);
const createError = ref('');

const categoryOptions = computed<{ label: string; value: TicketCategory }[]>(() => [
  { label: t('featureRequestWizard.category.uiUx'), value: 'UI_UX' },
  { label: t('featureRequestWizard.category.billing'), value: 'BILLING' },
  { label: t('featureRequestWizard.category.edi'), value: 'EDI' },
  { label: t('featureRequestWizard.category.reporting'), value: 'REPORTING' },
  { label: t('featureRequestWizard.category.performance'), value: 'PERFORMANCE' },
  { label: t('featureRequestWizard.category.integration'), value: 'INTEGRATION' },
  { label: t('featureRequestWizard.category.operations'), value: 'OPERATIONS' },
  { label: t('featureRequestWizard.category.compliance'), value: 'COMPLIANCE' },
  { label: t('featureRequestWizard.category.other'), value: 'OTHER' },
]);

// Step 2 chat
const chatInput = ref('');

// ── Helpers ─────────────────────────────────────────────────────────────────

const scrollToBottom = async () => {
  await nextTick();
  if (chatContainerRef.value) {
    chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight;
  }
};

const getStatusBadgeClasses = (status: string) => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold border';
  switch (status) {
    case 'DRAFT':
      return `${base} bg-gray-100 text-gray-700 border-gray-200`;
    case 'CLARIFYING':
      return `${base} bg-yellow-100 text-yellow-800 border-yellow-200`;
    case 'READY_FOR_REVIEW':
      return `${base} bg-green-100 text-green-800 border-green-200`;
    case 'APPROVED':
      return `${base} bg-blue-100 text-blue-800 border-blue-200`;
    case 'REJECTED':
      return `${base} bg-red-100 text-red-800 border-red-200`;
    case 'IN_PROGRESS':
      return `${base} bg-indigo-100 text-indigo-800 border-indigo-200`;
    case 'DONE':
      return `${base} bg-emerald-100 text-emerald-800 border-emerald-200`;
    default:
      return `${base} bg-gray-100 text-gray-600 border-gray-200`;
  }
};

// ── Actions ─────────────────────────────────────────────────────────────────

function openModal() {
  isOpen.value = true;
}

function closeModal() {
  isOpen.value = false;
  // Reset everything when user explicitly closes
  activeRequest.value = null;
  newTitle.value = '';
  newDescription.value = '';
  newCategory.value = '';
  chatInput.value = '';
  createError.value = '';
}

async function submitNewRequest() {
  if (!newTitle.value.trim()) return;
  isCreating.value = true;
  createError.value = '';
  try {
    const created = await createFeatureRequest({
      title: newTitle.value.trim(),
      description: newDescription.value.trim(),
      category: newCategory.value || undefined,
    });
    activeRequest.value = created;
    await scrollToBottom();
  } catch {
    createError.value = t('featureRequestWizard.error.submitFailed');
  } finally {
    isCreating.value = false;
  }
}

async function sendMessage() {
  if (!chatInput.value.trim() || !activeRequest.value?.id || isSending.value) return;

  const messageText = chatInput.value.trim();
  chatInput.value = '';

  // Optimistic update — add user message immediately
  const optimisticMsg: ConversationMessage = {
    role: 'user',
    content: messageText,
    timestamp: new Date().toISOString(),
  };
  if (!activeRequest.value.conversation) {
    activeRequest.value.conversation = [];
  }
  activeRequest.value.conversation.push(optimisticMsg);
  await scrollToBottom();

  isSending.value = true;
  try {
    const updated = await sendChatMessage(activeRequest.value.id, messageText);
    activeRequest.value = updated;
    await scrollToBottom();
  } catch {
    // Keep the optimistic message but mark it somehow — append error note
    activeRequest.value.conversation?.push({
      role: 'assistant',
      content: t('featureRequestWizard.error.chatFailed'),
      timestamp: new Date().toISOString(),
    });
    await scrollToBottom();
  } finally {
    isSending.value = false;
  }
}

function handleChatKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
}

// Scroll to bottom whenever conversation changes
watch(
  () => activeRequest.value?.conversation?.length,
  async () => {
    await scrollToBottom();
  }
);
</script>

<template>
  <!-- ── Floating Action Button ────────────────────────────────────────────── -->
  <button
    @click="openModal"
    class="fixed bottom-6 right-6 z-50 flex items-center justify-center w-14 h-14 rounded-full shadow-xl bg-gradient-to-br from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white transition-all duration-200 hover:scale-110 focus:outline-none focus:ring-4 focus:ring-blue-300 focus:ring-offset-2"
    :title="t('featureRequestWizard.label.suggestAFeature')"
    :aria-label="t('featureRequestWizard.label.suggestAFeature')"
  >
    <Lightbulb class="w-6 h-6" />
  </button>

  <!-- ── Modal ─────────────────────────────────────────────────────────────── -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition ease-out duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition ease-in duration-150"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 bg-gray-900/60 backdrop-blur-sm flex items-end sm:items-center justify-center z-[100] p-0 sm:p-4"
        @click.self="closeModal"
      >
        <Transition
          enter-active-class="transition ease-out duration-300"
          enter-from-class="translate-y-8 opacity-0 scale-95"
          enter-to-class="translate-y-0 opacity-100 scale-100"
          leave-active-class="transition ease-in duration-200"
          leave-from-class="translate-y-0 opacity-100 scale-100"
          leave-to-class="translate-y-8 opacity-0 scale-95"
          appear
        >
          <div
            v-if="isOpen"
            class="bg-white rounded-t-2xl sm:rounded-2xl shadow-2xl w-full sm:max-w-lg flex flex-col overflow-hidden"
            style="max-height: 90dvh;"
          >
            <!-- ── Step 1: New Request Form ─────────────────────────────── -->
            <template v-if="!activeRequest">
              <!-- Header -->
              <div class="flex items-center justify-between px-6 py-5 border-b border-gray-100">
                <div class="flex items-center gap-3">
                  <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center flex-shrink-0">
                    <Lightbulb class="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h2 class="text-base font-semibold text-gray-900">{{ t('featureRequestWizard.header.title') }}</h2>
                    <p class="text-xs text-gray-500">{{ t('featureRequestWizard.header.subtitle') }}</p>
                  </div>
                </div>
                <button
                  @click="closeModal"
                  class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                  :aria-label="t('common.close')"
                >
                  <X class="w-5 h-5" />
                </button>
              </div>

              <!-- Form -->
              <div class="flex-1 overflow-y-auto px-6 py-5 space-y-4">
                <!-- Error -->
                <div v-if="createError" class="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                  {{ createError }}
                </div>

                <!-- Title -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">
                    {{ t('featureRequestWizard.field.title') }} <span class="text-red-500">*</span>
                  </label>
                  <input
                    v-model="newTitle"
                    type="text"
                    :placeholder="t('featureRequestWizard.placeholder.title')"
                    class="block w-full border border-gray-300 rounded-xl py-2.5 px-3.5 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
                    @keyup.enter="newDescription && submitNewRequest()"
                    :disabled="isCreating"
                  />
                </div>

                <!-- Description -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">{{ t('featureRequestWizard.field.description') }}</label>
                  <textarea
                    v-model="newDescription"
                    rows="4"
                    :placeholder="t('featureRequestWizard.placeholder.description')"
                    class="block w-full border border-gray-300 rounded-xl py-2.5 px-3.5 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-none transition-colors"
                    :disabled="isCreating"
                  ></textarea>
                </div>

                <!-- Category -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1.5">{{ t('featureRequestWizard.field.category') }}</label>
                  <select
                    v-model="newCategory"
                    :disabled="isCreating"
                    class="block w-full border border-gray-300 rounded-xl py-2.5 px-3.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors bg-white"
                  >
                    <option value="">{{ t('featureRequestWizard.option.selectCategory') }}</option>
                    <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </option>
                  </select>
                </div>
              </div>

              <!-- Footer -->
              <div class="px-6 py-4 bg-gray-50 border-t border-gray-100 flex items-center justify-between gap-3">
                <p class="text-xs text-gray-400">{{ t('featureRequestWizard.footer.aiHint') }}</p>
                <button
                  @click="submitNewRequest"
                  :disabled="!newTitle.trim() || isCreating"
                  class="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-white bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 flex-shrink-0"
                >
                  <Loader2 v-if="isCreating" class="w-4 h-4 animate-spin" />
                  <Send v-else class="w-4 h-4" />
                  {{ isCreating ? t('featureRequestWizard.action.sending') : t('featureRequestWizard.action.send') }}
                </button>
              </div>
            </template>

            <!-- ── Step 2: Chat with AI ─────────────────────────────────── -->
            <template v-else>
              <!-- Header -->
              <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-white">
                <div class="flex items-center gap-3 min-w-0">
                  <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center flex-shrink-0">
                    <Bot class="w-4 h-4 text-white" />
                  </div>
                  <div class="min-w-0">
                    <!-- Ticket number badge -->
                    <div v-if="activeRequest.ticketNumber" class="mb-1">
                      <span class="font-mono text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200 px-2 py-0.5 rounded-full">
                        {{ t('featureRequestWizard.label.ticket', { number: activeRequest.ticketNumber }) }}
                      </span>
                    </div>
                    <p class="text-sm font-semibold text-gray-900 truncate">{{ activeRequest.title }}</p>
                    <span :class="getStatusBadgeClasses(activeRequest.status)">
                      {{ activeRequest.status.replace('_', ' ') }}
                    </span>
                  </div>
                </div>
                <button
                  @click="closeModal"
                  class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0 ml-2"
                  :aria-label="t('common.close')"
                >
                  <X class="w-5 h-5" />
                </button>
              </div>

              <!-- Messages -->
              <div
                ref="chatContainerRef"
                class="flex-1 overflow-y-auto px-4 py-4 space-y-4"
                style="min-height: 240px; max-height: 50dvh;"
              >
                <!-- Empty state -->
                <div
                  v-if="!activeRequest.conversation?.length"
                  class="flex flex-col items-center justify-center h-32 text-gray-400"
                >
                  <Bot class="w-8 h-8 mb-2 text-indigo-300" />
                  <p class="text-sm">{{ t('featureRequestWizard.chat.analysing') }}</p>
                </div>

                <!-- Messages -->
                <template v-else>
                  <div
                    v-for="(msg, index) in activeRequest.conversation"
                    :key="index"
                    class="flex"
                    :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
                  >
                    <!-- Assistant message -->
                    <div v-if="msg.role === 'assistant'" class="flex items-start gap-2 max-w-[85%]">
                      <div class="w-7 h-7 rounded-full bg-gradient-to-br from-indigo-400 to-blue-500 flex items-center justify-center flex-shrink-0 mt-0.5 shadow-sm">
                        <Bot class="w-3.5 h-3.5 text-white" />
                      </div>
                      <div class="bg-gradient-to-br from-slate-50 to-blue-50 border border-blue-100 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
                        <p class="text-sm text-gray-800 leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                        <p v-if="msg.timestamp" class="text-xs text-gray-400 mt-1.5">
                          {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                        </p>
                      </div>
                    </div>

                    <!-- User message -->
                    <div v-else class="max-w-[85%]">
                      <div class="bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl rounded-tr-sm px-4 py-3 shadow-sm">
                        <p class="text-sm text-white leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                        <p v-if="msg.timestamp" class="text-xs text-blue-200 mt-1.5 text-right">
                          {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                        </p>
                      </div>
                    </div>
                  </div>
                </template>

                <!-- Typing indicator -->
                <div v-if="isSending" class="flex items-start gap-2">
                  <div class="w-7 h-7 rounded-full bg-gradient-to-br from-indigo-400 to-blue-500 flex items-center justify-center flex-shrink-0 shadow-sm">
                    <Bot class="w-3.5 h-3.5 text-white" />
                  </div>
                  <div class="bg-gradient-to-br from-slate-50 to-blue-50 border border-blue-100 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
                    <div class="flex items-center gap-1">
                      <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                      <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                      <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Clarifications done banner -->
              <div
                v-if="activeRequest.clarificationsDone || activeRequest.status === 'READY_FOR_REVIEW'"
                class="mx-4 mb-4 bg-green-50 border border-green-200 rounded-xl p-3.5 flex items-start gap-3"
              >
                <CheckCircle class="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <div>
                  <p class="text-sm font-medium text-green-800">{{ t('featureRequestWizard.banner.allAnswered') }}</p>
                  <p class="text-xs text-green-700 mt-0.5">{{ t('featureRequestWizard.banner.awaitingReview') }}</p>
                </div>
              </div>

              <!-- Chat input -->
              <div
                v-else
                class="px-4 py-4 border-t border-gray-100 bg-white"
              >
                <div class="flex items-end gap-2 bg-gray-50 border border-gray-200 rounded-2xl px-3.5 py-2.5 focus-within:ring-2 focus-within:ring-blue-500 focus-within:border-blue-500 transition-all">
                  <textarea
                    v-model="chatInput"
                    rows="1"
                    :placeholder="t('featureRequestWizard.placeholder.chatReply')"
                    class="flex-1 bg-transparent text-sm text-gray-800 placeholder-gray-400 focus:outline-none resize-none max-h-28 leading-relaxed"
                    @keydown="handleChatKeydown"
                    :disabled="isSending"
                    style="min-height: 24px;"
                  ></textarea>
                  <button
                    @click="sendMessage"
                    :disabled="!chatInput.trim() || isSending"
                    class="flex-shrink-0 w-8 h-8 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed text-white flex items-center justify-center transition-all duration-150 shadow-sm"
                    :aria-label="t('featureRequestWizard.label.send')"
                  >
                    <Send class="w-3.5 h-3.5" />
                  </button>
                </div>
                <p class="text-xs text-gray-400 mt-1.5 px-1">{{ t('featureRequestWizard.chat.keyboardHint') }}</p>
              </div>
            </template>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
