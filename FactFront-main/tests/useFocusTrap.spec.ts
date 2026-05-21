import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, ref, h } from 'vue';
import { useFocusTrap } from '../src/composables/useFocusTrap';

const Harness = defineComponent({
  props: { active: { type: Boolean, default: false } },
  setup(props) {
    const root = ref<HTMLElement | null>(null);
    useFocusTrap(root, () => props.active);
    return () =>
      h('div', { ref: root }, [
        h('button', { id: 'a' }, 'A'),
        h('button', { id: 'b' }, 'B'),
        h('button', { id: 'c' }, 'C'),
      ]);
  },
});

describe('useFocusTrap', () => {
  it('focuses the first focusable when activated', async () => {
    const wrapper = mount(Harness, { attachTo: document.body });
    await wrapper.setProps({ active: true });
    await new Promise((r) => setTimeout(r, 0));
    expect(document.activeElement?.id).toBe('a');
    wrapper.unmount();
  });

  it('cycles forward from last to first on Tab', async () => {
    const wrapper = mount(Harness, { attachTo: document.body });
    await wrapper.setProps({ active: true });
    await new Promise((r) => setTimeout(r, 0));
    const c = document.getElementById('c') as HTMLButtonElement;
    c.focus();
    const evt = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true, bubbles: true });
    document.dispatchEvent(evt);
    expect(document.activeElement?.id).toBe('a');
    expect(evt.defaultPrevented).toBe(true);
    wrapper.unmount();
  });

  it('cycles backward from first to last on Shift+Tab', async () => {
    const wrapper = mount(Harness, { attachTo: document.body });
    await wrapper.setProps({ active: true });
    await new Promise((r) => setTimeout(r, 0));
    const a = document.getElementById('a') as HTMLButtonElement;
    a.focus();
    const evt = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true, bubbles: true });
    document.dispatchEvent(evt);
    expect(document.activeElement?.id).toBe('c');
    wrapper.unmount();
  });

  it('restores focus to the previously active element when deactivated', async () => {
    const before = document.createElement('button');
    before.id = 'before';
    document.body.appendChild(before);
    before.focus();
    expect(document.activeElement?.id).toBe('before');

    const wrapper = mount(Harness, { attachTo: document.body });
    await wrapper.setProps({ active: true });
    await new Promise((r) => setTimeout(r, 0));
    expect(document.activeElement?.id).toBe('a');

    await wrapper.setProps({ active: false });
    await new Promise((r) => setTimeout(r, 0));
    expect(document.activeElement?.id).toBe('before');

    wrapper.unmount();
    before.remove();
  });
});
