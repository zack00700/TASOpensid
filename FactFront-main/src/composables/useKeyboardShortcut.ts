import { onMounted, onBeforeUnmount, watch, type Ref } from 'vue';

type ShortcutHandler = (e: KeyboardEvent) => void;

interface ShortcutOptions {
  /** If provided, the shortcut is only active when the ref is truthy (e.g. a modal is open). */
  when?: Ref<boolean>;
  /** Prevent default browser behavior when the shortcut fires. Default: true. */
  preventDefault?: boolean;
  /** Ignore when focus is inside an input/textarea/contenteditable. Default: true. */
  ignoreInInputs?: boolean;
}

function isEditableTarget(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false;
  const tag = el.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    el.isContentEditable
  );
}

function matches(e: KeyboardEvent, combo: string): boolean {
  const parts = combo.toLowerCase().split('+').map(s => s.trim());
  const key = parts[parts.length - 1];
  const wantCtrl = parts.includes('ctrl') || parts.includes('mod');
  const wantMeta = parts.includes('meta') || parts.includes('cmd');
  const wantShift = parts.includes('shift');
  const wantAlt = parts.includes('alt');
  const keyMatch = e.key.toLowerCase() === key;
  return (
    keyMatch &&
    (wantCtrl ? e.ctrlKey : !e.ctrlKey) &&
    (wantMeta ? e.metaKey : !e.metaKey) &&
    (wantShift ? e.shiftKey : !e.shiftKey) &&
    (wantAlt ? e.altKey : !e.altKey)
  );
}

/**
 * Bind a keyboard shortcut to a handler. Combo syntax: "escape", "/", "n", "mod+k", "shift+?".
 */
export function useKeyboardShortcut(
  combo: string | string[],
  handler: ShortcutHandler,
  options: ShortcutOptions = {}
): void {
  const combos = Array.isArray(combo) ? combo : [combo];
  const preventDefault = options.preventDefault ?? true;
  const ignoreInInputs = options.ignoreInInputs ?? true;

  const listener = (e: KeyboardEvent) => {
    if (options.when && !options.when.value) return;
    if (ignoreInInputs && isEditableTarget(e.target)) {
      // Escape is the only shortcut that should still fire from inputs (to close modals).
      if (e.key !== 'Escape') return;
    }
    if (combos.some(c => matches(e, c))) {
      if (preventDefault) e.preventDefault();
      handler(e);
    }
  };

  onMounted(() => window.addEventListener('keydown', listener));
  onBeforeUnmount(() => window.removeEventListener('keydown', listener));

  // When the gating ref flips off, we keep the listener (low cost) but the handler becomes inert.
  if (options.when) {
    watch(options.when, () => {}, { immediate: false });
  }
}

/**
 * Shortcut for the very common case of closing a modal on Escape.
 * Pass a ref that, when truthy, indicates the modal is open, and a close fn.
 */
export function useEscapeToClose(isOpen: Ref<unknown>, close: () => void): void {
  useKeyboardShortcut(
    'Escape',
    () => {
      if (isOpen.value) close();
    },
    { ignoreInInputs: false },
  );
}
