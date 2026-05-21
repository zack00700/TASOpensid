import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ItemForm from '../src/components/ItemForm.vue';
import type { IsoContainerCode } from '../src/types/iso-code';
import { i18n } from '../src/i18n';

vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({ hasRole: () => true, isAdmin: () => false }),
}));

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: { value: [] },
    createMinimal: vi.fn(),
  }),
}));

function makeIsoCode(over: Partial<IsoContainerCode> = {}): IsoContainerCode {
  return {
    code: '22G1',
    description: "20' GP 8'6\"",
    lengthFt: 20, heightFt: 8.5, typeGroup: 'G',
    isReefer: false, isHazmatCapable: false, isTank: false, isOpenTop: false,
    isStandard: true, isActive: true, archetypeId: null,
    tareKg: 2300, maxPayloadKg: 28180, maxGrossKg: 30480,
    ...over,
  };
}

const fixtures = {
  active: [
    makeIsoCode({ code: '45G1', description: "40' HC GP" }),
    makeIsoCode({ code: '22G1', description: "20' GP 8'6\"" }),
    makeIsoCode({ code: '22R1', description: "20' Reefer", isReefer: true }),
  ],
  inactive: makeIsoCode({ code: 'OLDC', description: "Deprecated", isActive: false }),
};

const axiosMock = {
  get: vi.fn().mockImplementation((url: string) => {
    if (url === 'iso-codes') return Promise.resolve({ status: 200, data: fixtures.active });
    return Promise.resolve({ status: 200, data: [] });
  }),
  post: vi.fn(), put: vi.fn(), delete: vi.fn(),
};

function mountForm() {
  setActivePinia(createPinia());
  return mount(ItemForm, {
    props: {
      initialData: {
        itemNumber: '',
        itemType: 'container',
        type: '',
        ownerId: '',
        position: '',
        status: 'Available',
        lastInspection: '',
        nextInspection: '',
        notes: '',
        lifeCycles: [],
      },
    },
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
      stubs: { teleport: true },
    },
  });
}

describe('ItemForm — ISO code dropdown', () => {
  beforeEach(() => {
    axiosMock.get.mockClear();
    axiosMock.get.mockImplementation((url: string) => {
      if (url === 'iso-codes') return Promise.resolve({ status: 200, data: fixtures.active });
      return Promise.resolve({ status: 200, data: [] });
    });
  });

  it('populates the container-type dropdown with active ISO codes from the registry', async () => {
    const wrapper = mountForm();
    await flushPromises();
    expect(axiosMock.get).toHaveBeenCalledWith('iso-codes', { params: { includeInactive: false } });
    const html = wrapper.html();
    expect(html).toContain("22G1 — 20' GP");
    expect(html).toContain("22R1 — 20' Reefer");
    expect(html).toContain("40' HC GP");
  });

  it('filters out isActive=false codes client-side as defense-in-depth', async () => {
    const localFixtures = [...fixtures.active, fixtures.inactive];
    axiosMock.get.mockImplementation((url: string) => {
      if (url === 'iso-codes') return Promise.resolve({ status: 200, data: localFixtures });
      return Promise.resolve({ status: 200, data: [] });
    });
    const wrapper = mountForm();
    await flushPromises();
    expect(wrapper.html()).not.toContain('OLDC');
  });

  it('sorts options alphabetically by code', async () => {
    const wrapper = mountForm();
    await flushPromises();
    const codes = wrapper.findAll('option')
      .map(o => (o.element as HTMLOptionElement).value)
      .filter(v => /^[0-9A-Z]{4}$/.test(v));
    // expect ascending: 22G1 < 22R1 < 45G1 (deduped if appearing in 2 dropdowns)
    const unique = Array.from(new Set(codes));
    expect(unique).toEqual(['22G1', '22R1', '45G1']);
  });
});
