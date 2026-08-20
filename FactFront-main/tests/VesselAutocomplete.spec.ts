import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import VesselAutocomplete from '../src/components/ui/VesselAutocomplete.vue';
import type { Vessel } from '../src/types/vessel';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
};

const vesselList: Vessel[] = [
  { id: 'v1', name: 'MV Alpha', imoNumber: '9876543', callSign: 'AAA', flag: 'FR', owner: 'A', operator: 'A', vesselType: 'Container', status: 'Active' },
  { id: 'v2', name: 'MV Beta', imoNumber: '9876544', callSign: 'BBB', flag: 'DE', owner: 'B', operator: 'B', vesselType: 'Container', status: 'Active' },
  { id: 'v3', name: 'CMA Echo', imoNumber: '9876545', callSign: 'CCC', flag: 'FR', owner: 'C', operator: 'C', vesselType: 'Container', status: 'Active' },
];

function mountAc(modelValue = '') {
  axiosMock.get.mockResolvedValueOnce({ data: vesselList });
  return mount(VesselAutocomplete, {
    props: { modelValue },
    global: { provide: { $axios: axiosMock } },
  });
}

describe('VesselAutocomplete', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.put.mockReset();
  });

  it('renders an input bound to modelValue', async () => {
    const wrapper = mountAc('MV Alpha');
    await flushPromises();
    const input = wrapper.find<HTMLInputElement>('input');
    expect(input.exists()).toBe(true);
    expect(input.element.value).toBe('MV Alpha');
  });

  it('suggests vessels matching the typed query', async () => {
    const wrapper = mountAc('');
    await flushPromises();

    const input = wrapper.find<HTMLInputElement>('input');
    await input.setValue('MV');
    await flushPromises();

    const items = wrapper.findAll('[role="option"]');
    expect(items.length).toBe(2);
    expect(items[0].text()).toContain('MV Alpha');
    expect(items[1].text()).toContain('MV Beta');
  });

  it('emits update:modelValue when user types', async () => {
    const wrapper = mountAc('');
    await flushPromises();

    await wrapper.find<HTMLInputElement>('input').setValue('CMA');

    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    expect(emitted![emitted!.length - 1]).toEqual(['CMA']);
  });

  it('emits "select" with the full Vessel when a suggestion is clicked', async () => {
    const wrapper = mountAc('');
    await flushPromises();

    await wrapper.find<HTMLInputElement>('input').setValue('CMA');
    await flushPromises();

    await wrapper.find('[role="option"]').trigger('mousedown');
    await flushPromises();

    const selectEmits = wrapper.emitted('select');
    expect(selectEmits).toBeTruthy();
    expect(selectEmits![0][0]).toMatchObject({ name: 'CMA Echo', imoNumber: '9876545' });
  });

  it('emits "clear" when the typed value does not match any known vessel', async () => {
    const wrapper = mountAc('');
    await flushPromises();

    await wrapper.find<HTMLInputElement>('input').setValue('Unknown Vessel');
    await flushPromises();

    const clearEmits = wrapper.emitted('clear');
    expect(clearEmits).toBeTruthy();
  });

  it('does NOT emit "clear" when the typed value matches a known vessel exactly', async () => {
    const wrapper = mountAc('');
    await flushPromises();

    await wrapper.find<HTMLInputElement>('input').setValue('MV Alpha');
    await flushPromises();

    const clearEmits = wrapper.emitted('clear');
    expect(clearEmits).toBeFalsy();
  });
});
