import { describe, it, expect, vi } from 'vitest';
import { defineComponent } from 'vue';
import { mount } from '@vue/test-utils';
import { useInvoice } from '../src/composables/use.invoice';

describe('normalizeSort', () => {
  it('returns valid sort without warning', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const TestComponent = defineComponent({
      setup() {
        const { hydrateFromUrl, state } = useInvoice();
        hydrateFromUrl('?sort=createdDate:desc');
        return { state };
      },
      template: '<div></div>',
    });
    const wrapper = mount(TestComponent, {
      global: { provide: { $axios: {} } },
    });
    expect(wrapper.vm.state.sort).toBe('createdDate:desc');
    expect(warnSpy).not.toHaveBeenCalled();
    warnSpy.mockRestore();
  });
});
