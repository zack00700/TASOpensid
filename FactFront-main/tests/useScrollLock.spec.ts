import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { useScrollLock, __resetScrollLockForTests } from '../src/composables/useScrollLock';

const Harness = defineComponent({
  props: { active: { type: Boolean, default: false } },
  setup(props) {
    useScrollLock(() => props.active);
    return () => h('div');
  },
});

beforeEach(() => {
  __resetScrollLockForTests();
  document.documentElement.style.overflow = '';
  document.documentElement.style.paddingRight = '';
});

describe('useScrollLock', () => {
  it('sets overflow:hidden on <html> when active', async () => {
    const wrapper = mount(Harness);
    await wrapper.setProps({ active: true });
    expect(document.documentElement.style.overflow).toBe('hidden');
    wrapper.unmount();
  });

  it('reverts overflow when deactivated', async () => {
    const wrapper = mount(Harness);
    await wrapper.setProps({ active: true });
    await wrapper.setProps({ active: false });
    expect(document.documentElement.style.overflow).toBe('');
    wrapper.unmount();
  });

  it('reverts overflow on unmount even if still active', async () => {
    const wrapper = mount(Harness);
    await wrapper.setProps({ active: true });
    wrapper.unmount();
    expect(document.documentElement.style.overflow).toBe('');
  });

  it('handles two concurrent locks via reference counting', async () => {
    const a = mount(Harness);
    const b = mount(Harness);
    await a.setProps({ active: true });
    await b.setProps({ active: true });
    expect(document.documentElement.style.overflow).toBe('hidden');
    await a.setProps({ active: false });
    expect(document.documentElement.style.overflow).toBe('hidden'); // b still active
    await b.setProps({ active: false });
    expect(document.documentElement.style.overflow).toBe('');
    a.unmount();
    b.unmount();
  });
});
