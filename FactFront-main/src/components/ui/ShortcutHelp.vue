<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 bg-slate-900 bg-opacity-50 flex items-center justify-center z-[60] p-4"
      @click.self="close"
    >
      <div class="bg-white rounded-xl shadow-xl max-w-md w-full">
        <div class="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <Keyboard class="w-4 h-4 text-indigo-500" />
            <h3 class="text-sm font-semibold text-slate-900">Raccourcis clavier</h3>
          </div>
          <button
            class="text-slate-400 hover:text-slate-600 p-1 rounded hover:bg-slate-100"
            @click="close"
            aria-label="Fermer"
          >
            <X class="w-4 h-4" />
          </button>
        </div>

        <div class="p-5">
          <ul class="space-y-3 text-sm">
            <li v-for="s in shortcuts" :key="s.keys" class="flex items-center justify-between gap-4">
              <span class="text-slate-700">{{ s.description }}</span>
              <span class="flex items-center gap-1">
                <kbd
                  v-for="(k, i) in s.keys.split('+')"
                  :key="i"
                  class="inline-flex items-center justify-center min-w-[1.5rem] h-6 px-1.5 rounded border border-slate-200 bg-slate-50 text-slate-700 text-xs font-mono font-semibold shadow-sm"
                >{{ k }}</kbd>
              </span>
            </li>
          </ul>
          <p class="mt-4 text-xs text-slate-500 border-t border-slate-100 pt-3">
            Appuie sur <kbd class="px-1 py-0.5 bg-slate-100 rounded text-xs font-mono">?</kbd>
            à tout moment pour rouvrir cette aide.
          </p>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Keyboard, X } from 'lucide-vue-next';
import { useKeyboardShortcut } from '../../composables/useKeyboardShortcut';

const open = ref(false);

const shortcuts = [
  { keys: '/', description: 'Focus la barre de recherche' },
  { keys: 'n', description: 'Créer (nouveau BL / item / etc.)' },
  { keys: 'r', description: 'Rafraîchir la liste' },
  { keys: 'Esc', description: 'Fermer le modal ouvert' },
  { keys: 'Shift+?', description: 'Afficher cette aide' },
];

function close() {
  open.value = false;
}

useKeyboardShortcut(['?', 'shift+/'], () => {
  open.value = !open.value;
});

useKeyboardShortcut('Escape', () => {
  if (open.value) open.value = false;
}, { ignoreInInputs: false });
</script>
