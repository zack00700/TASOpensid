import { beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

/**
 * Global test setup, registered via `test.setupFiles` in vite.config.ts.
 *
 * Everything here compensates for something the real app provides but a bare
 * `mount()` does not. Keep it to environment plumbing — never put fixtures or
 * behavioural stubs in this file, or specs start passing for reasons that have
 * nothing to do with the code under test.
 */

/**
 * A fresh Pinia per test.
 *
 * Stores are reached outside of component setup in two places that most specs
 * hit without meaning to: the router's `beforeEach` guard (src/router/index.ts)
 * reads the auth store, and `useInvoice()` reaches for the invoice store. Both
 * throw "getActivePinia() was called but there was no active Pinia" unless one
 * is active. Re-creating it per test also keeps store state from leaking
 * between specs.
 */
beforeEach(() => {
  setActivePinia(createPinia());
});

/**
 * jsdom ships no canvas implementation, so every chart.js render logs a noisy
 * "Not implemented: HTMLCanvasElement.prototype.getContext" error. The stub
 * returns a no-op 2D context so chart components mount silently; specs that
 * actually assert on chart output would need the real `canvas` package.
 */
if (typeof HTMLCanvasElement !== 'undefined') {
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
    canvas: document.createElement('canvas'),
    clearRect: vi.fn(),
    fillRect: vi.fn(),
    strokeRect: vi.fn(),
    beginPath: vi.fn(),
    closePath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    arc: vi.fn(),
    fill: vi.fn(),
    stroke: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    translate: vi.fn(),
    rotate: vi.fn(),
    scale: vi.fn(),
    setTransform: vi.fn(),
    setLineDash: vi.fn(),
    getLineDash: vi.fn(() => []),
    measureText: vi.fn(() => ({ width: 0 })),
    fillText: vi.fn(),
    strokeText: vi.fn(),
    createLinearGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
    createRadialGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
    createPattern: vi.fn(() => null),
    drawImage: vi.fn(),
    putImageData: vi.fn(),
    getImageData: vi.fn(() => ({ data: new Uint8ClampedArray() })),
  })) as unknown as HTMLCanvasElement['getContext'];
}
