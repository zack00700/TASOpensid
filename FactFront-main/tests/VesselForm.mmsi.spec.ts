import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount } from '@vue/test-utils';
import VesselForm from '../src/components/VesselForm.vue';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({
    thirdParties: ref([]),
    createMinimal: vi.fn(),
  }),
}));
vi.mock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => false }) }));

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: [] }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function mountForm(props: Record<string, any> = {}) {
  return mount(VesselForm, {
    props,
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('VesselForm — MMSI field', () => {
  beforeEach(() => {
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
    axiosMock.post.mockResolvedValue({ data: {} });
    axiosMock.put.mockResolvedValue({ data: {} });
  });

  it('renders the MMSI input bound to formData.mmsi', async () => {
    const wrapper = mountForm();
    await wrapper.vm.$nextTick();
    const mmsiInput = wrapper.find('[data-test="vessel-form-mmsi"]');
    expect(mmsiInput.exists()).toBe(true);
    expect(mmsiInput.attributes('inputmode')).toBe('numeric');
    expect(mmsiInput.attributes('maxlength')).toBe('9');
  });

  it('rejects an 8-digit MMSI on submit (no POST fires)', async () => {
    const wrapper = mountForm();
    Object.assign((wrapper.vm as any).formData, {
      name: 'MV Test',
      imoNumber: 'IMO1234567',
      mmsi: '12345678',
      callSign: 'TST',
      flag: 'FR',
      owner: 'Owner',
      vesselType: 'Container Ship',
      status: 'Active',
    });
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(axiosMock.post).not.toHaveBeenCalled();
    expect((wrapper.vm as any).errors.mmsi).toMatch(/9 digits/i);
  });

  it('accepts an empty MMSI (POST fires)', async () => {
    const wrapper = mountForm();
    Object.assign((wrapper.vm as any).formData, {
      name: 'MV Test',
      imoNumber: 'IMO1234567',
      mmsi: '',
      callSign: 'TST',
      flag: 'FR',
      owner: 'Owner',
      vesselType: 'Container Ship',
      status: 'Active',
    });
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.vm.$nextTick();
    expect(axiosMock.post).toHaveBeenCalledWith('/vessel', expect.objectContaining({ name: 'MV Test', mmsi: '' }));
    expect((wrapper.vm as any).errors.mmsi).toBeUndefined();
  });
});
