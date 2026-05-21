import { describe, it, expect, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import Modal from '../src/components/ui/Modal.vue';
import { __resetScrollLockForTests } from '../src/composables/useScrollLock';

function findDialog(): HTMLElement | null {
  return document.body.querySelector<HTMLElement>('[role="dialog"]');
}

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
  // Belt and suspenders: scrub any teleported leftovers.
  document.body.querySelectorAll('[data-modal-panel], [data-modal-scrim]').forEach((n) => n.remove());
});

describe('Modal', () => {
  it('does not render when open=false', () => {
    trackedMount(Modal, { props: { open: false, title: 'Hi' } });
    expect(findDialog()).toBeNull();
  });

  it('renders a teleported dialog with correct ARIA when open=true', async () => {
    trackedMount(Modal, { props: { open: true, title: 'Hello' }, slots: { default: '<p>body</p>' } });
    await flushPromises();
    const dlg = findDialog();
    expect(dlg).toBeTruthy();
    expect(dlg!.getAttribute('aria-modal')).toBe('true');
    const labelledby = dlg!.getAttribute('aria-labelledby');
    expect(labelledby).toBeTruthy();
    expect(document.getElementById(labelledby!)?.textContent).toContain('Hello');
    expect(dlg!.textContent).toContain('body');
  });

  it('emits update:open=false on Escape when dismissible', async () => {
    const wrapper = trackedMount(Modal, { props: { open: true, title: 't' } });
    await flushPromises();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false]);
  });

  it('does not emit on Escape when dismissible=false', async () => {
    const wrapper = trackedMount(Modal, { props: { open: true, title: 't', dismissible: false } });
    await flushPromises();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await flushPromises();
    expect(wrapper.emitted('update:open')).toBeUndefined();
  });

  it('emits update:open=false when scrim is clicked', async () => {
    const wrapper = trackedMount(Modal, { props: { open: true, title: 't' } });
    await flushPromises();
    const scrim = document.body.querySelector<HTMLElement>('[data-modal-scrim]')!;
    scrim.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushPromises();
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false]);
  });

  it('does not close when click happens inside the panel', async () => {
    const wrapper = trackedMount(Modal, {
      props: { open: true, title: 't' },
      slots: { default: '<button id="inner">x</button>' },
    });
    await flushPromises();
    const inner = document.getElementById('inner')!;
    inner.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushPromises();
    expect(wrapper.emitted('update:open')).toBeUndefined();
  });

  it('locks body scroll while open', async () => {
    const wrapper = trackedMount(Modal, { props: { open: true, title: 't' } });
    await flushPromises();
    expect(document.documentElement.style.overflow).toBe('hidden');
    await wrapper.setProps({ open: false });
    await flushPromises();
    expect(document.documentElement.style.overflow).toBe('');
  });

  it('applies the size class for size="lg"', async () => {
    trackedMount(Modal, { props: { open: true, title: 't', size: 'lg' } });
    await flushPromises();
    const panel = document.body.querySelector<HTMLElement>('[data-modal-panel]')!;
    expect(panel.className).toMatch(/max-w-4xl/);
  });
});
