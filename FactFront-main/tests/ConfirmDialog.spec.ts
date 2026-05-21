import { describe, it, expect, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import ConfirmDialog from '../src/components/ui/ConfirmDialog.vue';
import { __resetScrollLockForTests } from '../src/composables/useScrollLock';

const mountedWrappers: VueWrapper[] = [];

function trackedMount(...args: Parameters<typeof mount>): ReturnType<typeof mount> {
  const wrapper = mount(...args);
  mountedWrappers.push(wrapper as VueWrapper);
  return wrapper;
}

afterEach(() => {
  for (const w of mountedWrappers) w.unmount();
  mountedWrappers.length = 0;
  __resetScrollLockForTests();
  document.body.querySelectorAll('[data-modal-panel], [data-modal-scrim]').forEach((n) => n.remove());
});

function findDialog(): HTMLElement | null {
  return document.body.querySelector<HTMLElement>('[role="dialog"]');
}

function findConfirm(): HTMLButtonElement {
  return document.body.querySelector<HTMLButtonElement>('[data-confirm-button]')!;
}

function findCancel(): HTMLButtonElement {
  return document.body.querySelector<HTMLButtonElement>('[data-cancel-button]')!;
}

describe('ConfirmDialog', () => {
  it('emits confirm when the primary button is clicked', async () => {
    const wrapper = trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Delete?', tone: 'danger' },
    });
    await flushPromises();
    findConfirm().click();
    expect(wrapper.emitted('confirm')).toHaveLength(1);
  });

  it('emits update:open=false when Cancel is clicked', async () => {
    const wrapper = trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Delete?', tone: 'danger' },
    });
    await flushPromises();
    findCancel().click();
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false]);
  });

  it('disables Cancel and suppresses Escape when loading=true', async () => {
    const wrapper = trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Delete?', tone: 'danger', loading: true },
    });
    await flushPromises();
    expect(findCancel().disabled).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.emitted('update:open')).toBeUndefined();
  });

  it('focuses Cancel by default for tone="danger"', async () => {
    trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Delete?', tone: 'danger' },
      attachTo: document.body,
    });
    await flushPromises();
    await new Promise((r) => setTimeout(r, 0));
    expect(document.activeElement).toBe(findCancel());
  });

  it('focuses Confirm by default for tone="info"', async () => {
    trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Confirm', tone: 'info' },
      attachTo: document.body,
    });
    await flushPromises();
    await new Promise((r) => setTimeout(r, 0));
    expect(document.activeElement).toBe(findConfirm());
  });

  it('uses confirmLabel prop when provided', async () => {
    trackedMount(ConfirmDialog, {
      props: { open: true, title: 't', tone: 'danger', confirmLabel: 'Yeet' },
    });
    await flushPromises();
    expect(findConfirm().textContent).toContain('Yeet');
  });

  it('renders danger tone classes', async () => {
    trackedMount(ConfirmDialog, { props: { open: true, title: 't', tone: 'danger' } });
    await flushPromises();
    const dlg = findDialog()!;
    expect(dlg.querySelector('[data-tone-icon]')!.getAttribute('class')).toMatch(/text-red-600/);
  });

  it('traps Tab focus between Cancel and Confirm', async () => {
    trackedMount(ConfirmDialog, {
      props: { open: true, title: 'Confirm', tone: 'info' },
      attachTo: document.body,
    });
    await flushPromises();
    await new Promise((r) => setTimeout(r, 0));

    // Focus the last focusable (Confirm) and Tab forward — should cycle to Cancel.
    findConfirm().focus();
    expect(document.activeElement).toBe(findConfirm());
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true, bubbles: true }));
    expect(document.activeElement).toBe(findCancel());

    // Shift+Tab from Cancel — should cycle back to Confirm.
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true, bubbles: true }));
    expect(document.activeElement).toBe(findConfirm());
  });
});
