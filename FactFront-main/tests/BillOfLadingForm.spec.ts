import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { shallowMount } from '@vue/test-utils';
import BillOfLadingForm from '../src/components/BillOfLadingForm.vue';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref([]), users: ref([]) }),
}));

describe('BillOfLadingForm', () => {
  it('initializes formData.items as empty array when initialData.items is null', () => {
    const initialData = {
      blNumber: '',
      status: 'Draft',
      shipper: '',
      consignee: '',
      notifyParty: '',
      transportType: 'Vessel',
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
      items: null,
      transportSnapshot: null,
    };

    const wrapper = shallowMount(BillOfLadingForm, {
      props: { initialData },
      global: { plugins: [i18n] },
    });

    expect((wrapper.vm as any).formData.items).toEqual([]);
  });
});
