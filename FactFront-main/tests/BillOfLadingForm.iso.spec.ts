import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import BillOfLadingForm from '../src/components/BillOfLadingForm.vue';
import type { IsoContainerCode } from '../src/types/iso-code';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref([]), users: ref([]) }),
}));

vi.mock('../src/services/billOfLadingService', () => ({
  default: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    updateTransport: vi.fn(),
    refreshTransport: vi.fn(),
    mapVisitToSnapshot: vi.fn(),
  },
}));

vi.mock('../src/services/vesselVisitService', () => ({
  default: {
    list: vi.fn().mockResolvedValue([]),
    search: vi.fn().mockResolvedValue([]),
  },
  sanitizeVesselQuery: (q: string) => q,
}));

vi.mock('../src/services/itemService', () => ({
  default: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('../src/services/invoiceService', () => ({
  default: {
    create: vi.fn(),
  },
}));

function makeIsoCode(over: Partial<IsoContainerCode> = {}): IsoContainerCode {
  return {
    code: '22G1', description: "20' GP 8'6\"",
    lengthFt: 20, heightFt: 8.5, typeGroup: 'G',
    isReefer: false, isHazmatCapable: false, isTank: false, isOpenTop: false,
    isStandard: true, isActive: true, archetypeId: null,
    tareKg: 2300, maxPayloadKg: 28180, maxGrossKg: 30480,
    ...over,
  };
}

const fixtures = [
  makeIsoCode({ code: '22G1' }),
  makeIsoCode({ code: '45G1', description: "40' HC GP" }),
  makeIsoCode({ code: 'OLDC', description: "Deprecated", isActive: false }),
];

const axiosMock = {
  get: vi.fn().mockImplementation((url: string) => {
    if (url === 'iso-codes') return Promise.resolve({ status: 200, data: fixtures });
    return Promise.resolve({ status: 200, data: [] });
  }),
  post: vi.fn(), put: vi.fn(), delete: vi.fn(),
};

function mountForm() {
  setActivePinia(createPinia());
  return mount(BillOfLadingForm, {
    props: {
      initialData: {
        blNumber: '',
        status: 'Draft' as const,
        shipper: '',
        consignee: '',
        notifyParty: '',
        transportType: 'Vessel' as const,
        vessel: '',
        voyage: '',
        portOfLoading: '',
        portOfDischarge: '',
        placeOfDelivery: '',
        driver: '',
        trainNumber: '',
        truckNumber: '',
        commodity: {
          description: '',
          weightKg: 0,
          volumeM3: 0,
          packagesNumber: 0,
          hazardous: false,
        },
        items: [
          {
            id: 'i1',
            clientId: 'c1',
            itemType: 'container',
            type: '22G1',
            itemNumber: 'TCNU1234567',
            status: 'Available',
            ownerId: '',
            position: '',
            lastInspection: '',
            nextInspection: '',
            notes: '',
            weightKg: 0,
            volumeM3: 0,
            expanded: true,
          },
        ],
        transportSnapshot: null,
      },
    },
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
      stubs: { teleport: true, InvoicePreview: true, TypeaheadInput: true },
    },
  });
}

describe('BillOfLadingForm — ISO code dropdown', () => {
  beforeEach(() => {
    axiosMock.get.mockClear();
    axiosMock.get.mockImplementation((url: string) => {
      if (url === 'iso-codes') return Promise.resolve({ status: 200, data: fixtures });
      return Promise.resolve({ status: 200, data: [] });
    });
  });

  it('populates the container-type dropdown with active ISO codes from the registry', async () => {
    const wrapper = mountForm();
    await flushPromises();
    // Wizard defaults to step "basic" — jump to "items" step where the dropdown lives
    (wrapper.vm as any).activeStep = 'items';
    // Force the item card to expand so the container-type dropdown renders
    const items = (wrapper.vm as any).formData.items;
    if (items?.[0]) items[0].expanded = true;
    await wrapper.vm.$nextTick();
    await flushPromises();
    expect(axiosMock.get).toHaveBeenCalledWith('iso-codes', { params: { includeInactive: false } });
    const html = wrapper.html();
    expect(html).toContain('22G1');
    expect(html).toContain('45G1');
    expect(html).not.toContain('OLDC');  // inactive filtered
  });

  it('preserves the selected value when initialData provides a containerType', async () => {
    const wrapper = mountForm();
    await flushPromises();
    (wrapper.vm as any).activeStep = 'items';
    // Force the item card to expand so the container-type dropdown renders
    const items = (wrapper.vm as any).formData.items;
    if (items?.[0]) items[0].expanded = true;
    await wrapper.vm.$nextTick();
    await flushPromises();
    // The "items[0].type" was set to '22G1' in initialData → the corresponding option should be selected
    const selectedOption = wrapper.find('option[value="22G1"]');
    expect(selectedOption.exists()).toBe(true);
  });
});
