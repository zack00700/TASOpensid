import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import CreateThirdPartyModal from '../src/components/ui/CreateThirdPartyModal.vue';
import { i18n } from '../src/i18n';

const createMinimal = vi.fn();
vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref([]), createMinimal }),
}));

describe('CreateThirdPartyModal', () => {
  it('pre-fills companyName and industryType from props', () => {
    const w = mount(CreateThirdPartyModal, {
      global: { plugins: [i18n] },
      props: { open: true, initialName: 'NewCo', initialIndustryType: 'Shipping Line' },
    });
    expect((w.find('input[name="companyName"]').element as HTMLInputElement).value).toBe('NewCo');
    expect((w.find('select[name="industryType"]').element as HTMLSelectElement).value).toBe('Shipping Line');
  });

  it('calls createMinimal on submit and emits created with the returned entity', async () => {
    createMinimal.mockResolvedValueOnce({
      id: 'x', companyName: 'NewCo', industryType: 'Shipping Line', companyAddress: '1 rue de Paris',
    });
    const w = mount(CreateThirdPartyModal, {
      global: { plugins: [i18n] },
      props: { open: true, initialName: 'NewCo', initialIndustryType: 'Shipping Line' },
    });
    await w.find('input[name="companyAddress"]').setValue('1 rue de Paris');
    await w.find('form').trigger('submit.prevent');
    await flushPromises();
    expect(createMinimal).toHaveBeenCalledWith({
      companyName: 'NewCo',
      industryType: 'Shipping Line',
      companyAddress: '1 rue de Paris',
    });
    expect(w.emitted('created')?.[0]?.[0]).toMatchObject({ id: 'x' });
  });
});
