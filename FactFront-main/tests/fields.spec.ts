import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import Fields from '../src/components/Fields.vue';
import { i18n } from '../src/i18n';
import type { Field } from '../src/types/field';

const mocks = vi.hoisted(() => ({
  sampleFields: [
    {
      key: 'title',
      defaultValue: 'Title',
      translations: { es: 'Título' },
      pages: ['invoices'],
    },
  ] as Field[],
}));

vi.mock('../src/services/fieldService', () => ({
  default: {
    getFields: vi.fn().mockResolvedValue(mocks.sampleFields),
    addLanguage: vi.fn().mockResolvedValue(undefined),
    updateTranslation: vi.fn().mockResolvedValue(undefined),
  },
}));

import fieldService from '../src/services/fieldService';

describe('Fields component', () => {
  it('renders field data and translations', async () => {
    const wrapper = mount(Fields, { global: { plugins: [i18n] } });
    await flushPromises();
    expect(wrapper.text()).toContain('title');
    const input = wrapper.find('[data-test="translation-title-es"]');
    expect((input.element as HTMLInputElement).value).toBe('Título');
  });

  it('adds a new language', async () => {
    const wrapper = mount(Fields, { global: { plugins: [i18n] } });
    await flushPromises();
    await wrapper.find('[data-test="add-language-button"]').trigger('click');
    await wrapper.find('[data-test="new-language-input"]').setValue('fr');
    await wrapper.find('[data-test="save-language-button"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-test="lang-header-fr"]').exists()).toBe(true);
  });

  it('saves translation on blur', async () => {
    const wrapper = mount(Fields, { global: { plugins: [i18n] } });
    await flushPromises();
    const input = wrapper.find('[data-test="translation-title-es"]');
    await input.setValue('Nuevo');
    await input.trigger('blur');
    expect(fieldService.updateTranslation).toHaveBeenCalledWith('title', 'es', 'Nuevo');
  });
});
