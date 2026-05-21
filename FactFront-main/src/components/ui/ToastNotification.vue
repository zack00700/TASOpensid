<script setup lang="ts">
import type { Toast } from '../../composables/useToast'

defineProps<{
  toast: Toast | null
}>()

defineEmits<{ dismiss: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div
        v-if="toast"
        class="tn-root"
        :class="`tn-root--${toast.type}`"
        role="alert"
        aria-live="polite"
      >
        <!-- Icon -->
        <svg v-if="toast.type === 'success'" class="tn-icon" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
        </svg>
        <svg v-else-if="toast.type === 'error'" class="tn-icon" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
        </svg>
        <svg v-else class="tn-icon" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
        </svg>

        <span class="tn-msg">{{ toast.message }}</span>

        <button class="tn-dismiss" @click="$emit('dismiss')" aria-label="Dismiss">
          <svg viewBox="0 0 16 16" fill="currentColor" class="w-4 h-4">
            <path d="M4.646 4.646a.5.5 0 01.708 0L8 7.293l2.646-2.647a.5.5 0 01.708.708L8.707 8l2.647 2.646a.5.5 0 01-.708.708L8 8.707l-2.646 2.647a.5.5 0 01-.708-.708L7.293 8 4.646 5.354a.5.5 0 010-.708z"/>
          </svg>
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.tn-root {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 500;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14);
  min-width: 260px;
  max-width: 400px;
  border: 1px solid transparent;
}
.tn-root--success { background: #f0fdf4; color: #166534; border-color: #bbf7d0; }
.tn-root--error   { background: #fef2f2; color: #991b1b; border-color: #fecaca; }
.tn-root--warning { background: #fffbeb; color: #92400e; border-color: #fde68a; }

.tn-icon  { width: 1.1rem; height: 1.1rem; flex-shrink: 0; }
.tn-msg   { flex: 1; }
.tn-dismiss {
  background: none;
  border: none;
  cursor: pointer;
  color: currentColor;
  opacity: 0.5;
  padding: 0;
  line-height: 0;
  flex-shrink: 0;
}
.tn-dismiss:hover { opacity: 1; }

/* Transition */
.toast-enter-active, .toast-leave-active { transition: all 0.25s ease; }
.toast-enter-from  { opacity: 0; transform: translateY(12px) scale(0.95); }
.toast-leave-to    { opacity: 0; transform: translateY(6px) scale(0.97); }
</style>
