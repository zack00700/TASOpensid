import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount } from '@vue/test-utils';
import VesselVisitForm from '../src/components/VesselVisitForm.vue';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([
      { id: '1', companyName: 'Maersk', industryType: 'Shipping Line', companyAddress: 'Copenhagen' },
      { id: '2', companyName: 'Bolloré', industryType: 'Trucking Company', companyAddress: 'Paris' },
    ]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

describe('VesselVisitForm — line operator autocomplete', () => {
  it('filters Line Operator to Shipping Line tiers', async () => {
    const w = mount(VesselVisitForm, { global: { plugins: [i18n] }, props: {} });
    const combos = w.findAll('[role="combobox"]');
    expect(combos.length).toBeGreaterThan(0);
    await combos[combos.length - 1].setValue('a');
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items).toContain('Maersk');
    expect(items).not.toContain('Bolloré');
  });
});
