import { onBeforeUnmount, watch } from 'vue';

let lockCount = 0;
let savedOverflow = '';
let savedPaddingRight = '';

function applyLock() {
  if (lockCount === 0) {
    const html = document.documentElement;
    savedOverflow = html.style.overflow;
    savedPaddingRight = html.style.paddingRight;
    // Measure before setting overflow:hidden — needs the live scrollbar to compute width.
    const scrollbarWidth = window.innerWidth - html.clientWidth;
    html.style.overflow = 'hidden';
    if (scrollbarWidth > 0) {
      const computed = parseFloat(window.getComputedStyle(html).paddingRight) || 0;
      html.style.paddingRight = `${computed + scrollbarWidth}px`;
    }
  }
  lockCount += 1;
}

function releaseLock() {
  if (lockCount === 0) return;
  lockCount -= 1;
  if (lockCount === 0) {
    const html = document.documentElement;
    html.style.overflow = savedOverflow;
    html.style.paddingRight = savedPaddingRight;
  }
}

/** Test-only escape hatch. Do not use in production code. */
export function __resetScrollLockForTests(): void {
  lockCount = 0;
  savedOverflow = '';
  savedPaddingRight = '';
}

/**
 * Lock body (`<html>`) scroll while `isActive()` returns true.
 * Reference-counted: multiple concurrent consumers are supported.
 */
export function useScrollLock(isActive: () => boolean): void {
  let isHeld = false;

  watch(
    () => isActive(),
    (active) => {
      if (active && !isHeld) {
        applyLock();
        isHeld = true;
      } else if (!active && isHeld) {
        releaseLock();
        isHeld = false;
      }
    },
    { immediate: true },
  );

  onBeforeUnmount(() => {
    if (isHeld) {
      releaseLock();
      isHeld = false;
    }
  });
}
