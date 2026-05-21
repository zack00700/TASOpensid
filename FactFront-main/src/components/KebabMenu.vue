<script setup lang="ts">
import { ref, onBeforeUnmount, nextTick } from 'vue';

const isOpen = ref(false);
const triggerEl = ref<HTMLElement | null>(null);
const menuEl = ref<HTMLElement | null>(null);
const style = ref<{ top: string; left: string; transformOrigin: string }>({
  top: '0px',
  left: '0px',
  transformOrigin: 'top right'
});

function toggle() {
  isOpen.value ? close() : open();
}

function open() {
  isOpen.value = true;
  nextTick(() => {
    positionMenu();
    addListeners();
  });
}

function close() {
  isOpen.value = false;
  removeListeners();
}

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') close();
}

function onOutside(e: MouseEvent) {
  const path = (e.composedPath && e.composedPath()) || [];
  if (!path.includes(triggerEl.value as any) && !path.includes(menuEl.value as any)) {
    close();
  }
}

function onReposition() {
  if (isOpen.value) positionMenu();
}

function addListeners() {
  window.addEventListener('resize', onReposition, { passive: true });
  window.addEventListener('scroll', onReposition, { passive: true, capture: true });
  window.addEventListener('click', onOutside, { capture: true });
  window.addEventListener('keydown', onEsc);
}

function removeListeners() {
  window.removeEventListener('resize', onReposition);
  window.removeEventListener('scroll', onReposition, { capture: true } as any);
  window.removeEventListener('click', onOutside, { capture: true } as any);
  window.removeEventListener('keydown', onEsc);
}

function positionMenu() {
  const t = triggerEl.value?.getBoundingClientRect();
  const m = menuEl.value;
  if (!t || !m) return;

  const menuWidth = Math.min(m.offsetWidth || 200, 280);
  const menuHeight = Math.min(m.offsetHeight || 240, 360);

  const pad = 8;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const below = vh - t.bottom;
  const above = t.top;
  const openUp = below < menuHeight + pad && above > below;

  let top = openUp ? t.top - menuHeight - pad : t.bottom + pad;
  let left = Math.min(t.right - menuWidth, vw - menuWidth - pad);

  top = Math.max(pad, Math.min(top, vh - menuHeight - pad));
  left = Math.max(pad, Math.min(left, vw - menuWidth - pad));

  style.value = {
    top: `${Math.round(top)}px`,
    left: `${Math.round(left)}px`,
    transformOrigin: openUp ? 'bottom right' : 'top right'
  };
}

onBeforeUnmount(removeListeners);
</script>

<template>
  <span class="inline-flex">
    <slot name="trigger" :toggle="toggle" :refEl="(el: HTMLElement) => (triggerEl = el)" :isOpen="isOpen"></slot>
    <Teleport to="body" v-if="isOpen">
      <div
        ref="menuEl"
        class="fixed z-50 bg-white border rounded-lg shadow-lg overflow-auto min-w-[12rem] max-h-[min(280px,calc(100vh-16px))] focus:outline-none"
        :style="style"
        role="menu"
      >
        <slot name="content" :close="close"></slot>
      </div>
    </Teleport>
  </span>
</template>

