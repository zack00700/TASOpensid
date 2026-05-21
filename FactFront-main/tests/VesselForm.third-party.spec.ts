import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import VesselForm from '../src/components/VesselForm.vue';
import { i18n } from '../src/i18n';

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: [] }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([
      { id: '1', companyName: 'CMA CGM', industryType: 'Shipping Line', companyAddress: 'Marseille' },
      { id: '2', companyName: 'DB Schenker', industryType: 'Freight Forwarder', companyAddress: 'Essen' },
    ]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

describe('VesselForm — third-party autocomplete', () => {
  it('only suggests Shipping Line tiers for Owner and Operator', async () => {
    const w = mount(VesselForm, {
      global: { plugins: [i18n], provide: { $axios: axiosMock } },
      props: {},
    });
    const ownerInput = w.findAll('[role="combobox"]')[0];
    await ownerInput.setValue('a');
    await flushPromises();
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items).toContain('CMA CGM');
    expect(items).not.toContain('DB Schenker');
  });
});
