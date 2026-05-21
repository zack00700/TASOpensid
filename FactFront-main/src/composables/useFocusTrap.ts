import { onBeforeUnmount, watch, type Ref } from 'vue';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function getFocusables(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (el) => !el.hasAttribute('disabled'),
  );
}

/**
 * Trap Tab focus inside `containerRef` while `isActive()` returns true.
 * On activation, focuses the first focusable. On deactivation, restores
 * focus to the element that was active just before activation.
 */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  isActive: () => boolean,
): void {
  let previouslyFocused: HTMLElement | null = null;

  function handleKeydown(e: KeyboardEvent) {
    if (!isActive() || e.key !== 'Tab') return;
    const root = containerRef.value;
    if (!root) return;
    const focusables = getFocusables(root);
    if (focusables.length === 0) {
      e.preventDefault();
      return;
    }
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;

    if (e.shiftKey && (active === first || !root.contains(active))) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && (active === last || !root.contains(active))) {
      e.preventDefault();
      first.focus();
    }
  }

  watch(
    () => isActive(),
    (active) => {
      if (active) {
        previouslyFocused = document.activeElement as HTMLElement | null;
        // Defer to next tick so slot content is mounted.
        queueMicrotask(() => {
          const root = containerRef.value;
          if (!root) return;
          const focusables = getFocusables(root);
          (focusables[0] ?? root).focus();
        });
        document.addEventListener('keydown', handleKeydown);
      } else {
        document.removeEventListener('keydown', handleKeydown);
        if (previouslyFocused && document.contains(previouslyFocused)) {
          previouslyFocused.focus();
        }
        previouslyFocused = null;
      }
    },
    { immediate: true },
  );

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown);
  });
}
