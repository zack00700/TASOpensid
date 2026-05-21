import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import IsoCodes from '../src/components/IsoCodes.vue';
import type { IsoContainerCode } from '../src/types/iso-code';
import type { ContainerArchetype } from '../src/types/container-archetype';
import { i18n } from '../src/i18n';

vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({
    hasRole: (r: string) => r === 'ROLE_ADMIN',
  }),
}));

function makeCode(over: Partial<IsoContainerCode> = {}): IsoContainerCode {
  return {
    code: '22G1',
    description: '20\' GP',
    lengthFt: 20,
    heightFt: 8.5,
    typeGroup: 'G',
    isReefer: false,
    isHazmatCapable: false,
    isTank: false,
    isOpenTop: false,
    isStandard: true,
    isActive: true,
    archetypeId: null,
    tareKg: 2300,
    maxPayloadKg: 28180,
    maxGrossKg: 30480,
    ...over,
  };
}

const fixtures = {
  codes: [
    makeCode({ code: '22G1', typeGroup: 'G', isStandard: true }),
    makeCode({ code: '22R1', typeGroup: 'R', isReefer: true, isStandard: true }),
    makeCode({ code: 'CUST', isStandard: false }),
  ],
  archetypes: [] as ContainerArchetype[],
};

const axiosMock = {
  get: vi.fn(),
  post: vi.fn().mockResolvedValue({ status: 201, data: makeCode({ code: 'NEW1', isStandard: false }) }),
  put: vi.fn().mockResolvedValue({ status: 200, data: makeCode() }),
  delete: vi.fn().mockResolvedValue({ status: 204, data: '' }),
};

function mountPage() {
  axiosMock.get.mockImplementation((url: string) => {
    if (url === 'iso-codes') return Promise.resolve({ status: 200, data: fixtures.codes });
    if (url === 'archetypes') return Promise.resolve({ status: 200, data: fixtures.archetypes });
    return Promise.reject(new Error('unexpected url ' + url));
  });
  return mount(IsoCodes, {
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('IsoCodes — admin page', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockClear();
    axiosMock.put.mockClear();
    axiosMock.delete.mockClear();
  });

  it('renders one row per code returned by the API', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const rows = wrapper.findAll('[data-test^="iso-row-"]');
    expect(rows.length).toBe(3);
    expect(wrapper.text()).toContain('22G1');
    expect(wrapper.text()).toContain('22R1');
    expect(wrapper.text()).toContain('CUST');
  });

  it('filters rows by typeGroup when the filter is set', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('[data-test="iso-filter-typegroup"]').setValue('R');
    await wrapper.vm.$nextTick();
    const rows = wrapper.findAll('[data-test^="iso-row-"]');
    expect(rows.length).toBe(1);
    expect(wrapper.text()).toContain('22R1');
    expect(wrapper.text()).not.toContain('22G1');
  });

  it('disables the Delete button on standard codes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const standardDelete = wrapper.find('[data-test="iso-delete-22G1"]');
    expect(standardDelete.attributes('disabled')).toBeDefined();
    const customDelete = wrapper.find('[data-test="iso-delete-CUST"]');
    expect(customDelete.attributes('disabled')).toBeUndefined();
  });

  it('clicking Edit opens a modal pre-filled with the code', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('[data-test="iso-edit-22G1"]').trigger('click');
    await wrapper.vm.$nextTick();
    const modal = wrapper.find('[data-test="iso-modal"]');
    expect(modal.exists()).toBe(true);
    expect(modal.find('[data-test="iso-modal-code"]').element.textContent).toContain('22G1');
    const description = modal.find('textarea').element as HTMLTextAreaElement;
    expect(description.value).toBe("20' GP");
  });

  it('clicking Delete on a custom code calls DELETE and refreshes the list', async () => {
    const wrapper = mountPage();
    await flushPromises();
    axiosMock.get.mockClear();
    await wrapper.find('[data-test="iso-delete-CUST"]').trigger('click');
    await flushPromises();
    expect(axiosMock.delete).toHaveBeenCalledWith('iso-codes/CUST');
    expect(axiosMock.get).toHaveBeenCalledWith('iso-codes', { params: { includeInactive: false } });
  });
});
