import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import FilterBuilder from '../src/components/FilterBuilder.vue';
import { i18n } from '../src/i18n';

describe('FilterBuilder', () => {
  it('normalizes field keys and enforces array structure', async () => {
    const mockFields = {
      items: [{ field: 'size', label: 'Size', valueType: 'INT' }],
      bol: { field: 'eta', label: 'ETA', valueType: 'DATE' }
    };
    const get = vi.fn(() => Promise.resolve({ data: mockFields }));
    const wrapper = mount(FilterBuilder, {
      props: { modelValue: [] },
      global: {
        plugins: [i18n],
        provide: {
          $axios: { get }
        }
      }
    });

    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();

    expect(get).toHaveBeenCalledWith('/contract/filterable-fields');
    expect((wrapper.vm as any).filterableFields.ITEM).toEqual(mockFields.items);
    expect((wrapper.vm as any).filterableFields.BILL_OF_LADING).toEqual([]);
  });
});
