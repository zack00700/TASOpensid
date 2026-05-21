import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ContainerArchetypes from '../src/components/ContainerArchetypes.vue';
import type { ContainerArchetype } from '../src/types/container-archetype';
import type { IsoContainerCode } from '../src/types/iso-code';
import { i18n } from '../src/i18n';

vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({
    hasRole: (r: string) => r === 'ROLE_ADMIN',
  }),
}));

const archetypeWithCodes: ContainerArchetype = {
  id: 'arch-with-codes',
  code: 'DRY_20',
  name: '20\' Dry',
  description: '',
  isActive: true,
};
const archetypeEmpty: ContainerArchetype = {
  id: 'arch-empty',
  code: 'EMPTY',
  name: 'Empty Archetype',
  description: '',
  isActive: true,
};

const codesForDry20: IsoContainerCode[] = [
  {
    code: '22G1', description: '20\' GP', lengthFt: 20, heightFt: 8.5, typeGroup: 'G',
    isReefer: false, isHazmatCapable: false, isTank: false, isOpenTop: false,
    isStandard: true, isActive: true, archetypeId: 'arch-with-codes',
    tareKg: 2300, maxPayloadKg: 28180, maxGrossKg: 30480,
  },
];

const axiosMock = {
  get: vi.fn(),
  post: vi.fn().mockResolvedValue({ status: 201, data: { ...archetypeEmpty, id: 'new-arch' } }),
  put: vi.fn().mockResolvedValue({ status: 200, data: archetypeEmpty }),
  delete: vi.fn().mockResolvedValue({ status: 204, data: '' }),
};

function mountPage() {
  axiosMock.get.mockImplementation((url: string) => {
    if (url === 'archetypes') return Promise.resolve({ status: 200, data: [archetypeWithCodes, archetypeEmpty] });
    if (url === 'archetypes/arch-with-codes/iso-codes') return Promise.resolve({ status: 200, data: codesForDry20 });
    if (url === 'archetypes/arch-empty/iso-codes') return Promise.resolve({ status: 200, data: [] });
    return Promise.reject(new Error('unexpected url ' + url));
  });
  return mount(ContainerArchetypes, {
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('ContainerArchetypes — admin page', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockClear();
    axiosMock.put.mockClear();
    axiosMock.delete.mockClear();
  });

  it('renders one row per archetype with assigned-count badge', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const rows = wrapper.findAll('[data-test^="arch-row-"]');
    expect(rows.length).toBe(2);
    expect(wrapper.find('[data-test="arch-count-arch-with-codes"]').text()).toContain('1');
    expect(wrapper.find('[data-test="arch-count-arch-empty"]').text()).toContain('0');
  });

  it('clicking Edit opens a modal pre-filled with the archetype', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('[data-test="arch-edit-arch-with-codes"]').trigger('click');
    await wrapper.vm.$nextTick();
    const modal = wrapper.find('[data-test="arch-modal"]');
    expect(modal.exists()).toBe(true);
    expect(modal.find('[data-test="arch-modal-name"]').element.value).toBe('20\' Dry');
  });

  it('disables Delete when at least one ISO code is assigned', async () => {
    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.find('[data-test="arch-delete-arch-with-codes"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-test="arch-delete-arch-empty"]').attributes('disabled')).toBeUndefined();
  });

  it('clicking Add then Save POSTs the new archetype and refreshes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    axiosMock.get.mockClear();
    await wrapper.find('[data-test="arch-add"]').trigger('click');
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-test="arch-modal-code"]').setValue('NEW1');
    await wrapper.find('[data-test="arch-modal-name"]').setValue('New Archetype');
    await wrapper.find('[data-test="arch-modal-save"]').trigger('click');
    await flushPromises();
    expect(axiosMock.post).toHaveBeenCalledWith('archetypes', expect.objectContaining({ code: 'NEW1', name: 'New Archetype' }));
    expect(axiosMock.get).toHaveBeenCalledWith('archetypes');
  });
});
