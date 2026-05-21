import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import ThirdPartyAutocomplete from '../src/components/ui/ThirdPartyAutocomplete.vue';
import { i18n } from '../src/i18n';

const tps = [
  { id: '1', companyName: 'CMA CGM', industryType: 'Shipping Line', companyAddress: 'Marseille' },
  { id: '2', companyName: 'Maersk', industryType: 'Shipping Line', companyAddress: 'Copenhagen' },
  { id: '3', companyName: 'DB Schenker', industryType: 'Freight Forwarder', companyAddress: 'Essen' },
];

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref(tps), createMinimal: vi.fn() }),
}));

vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({ isAdmin: () => false }),
}));

function factory(props: Record<string, unknown> = {}) {
  return mount(ThirdPartyAutocomplete, {
    global: { plugins: [i18n] },
    props: { modelValue: '', label: 'Operator', ...props },
  });
}

describe('ThirdPartyAutocomplete', () => {
  it('renders an input bound to modelValue', () => {
    const w = factory({ modelValue: 'Maersk' });
    expect(w.find('input').element.value).toBe('Maersk');
  });

  it('emits update:modelValue when the user types', async () => {
    const w = factory();
    await w.find('input').setValue('Mae');
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['Mae']);
  });

  it('filters suggestions by industryType', async () => {
    const w = factory({ industryType: 'Shipping Line' });
    await w.find('input').setValue('a');           // triggers list open
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items).toContain('CMA CGM');
    expect(items).toContain('Maersk');
    expect(items).not.toContain('DB Schenker');
  });

  it('shows all third parties when industryType is not set', async () => {
    const w = factory();
    await w.find('input').setValue('e');
    const items = w.findAll('.ta-item').map((n) => n.text());
    expect(items.length).toBeGreaterThanOrEqual(2);
    expect(items.some((t) => t.includes('Schenker'))).toBe(true);
  });

  it('emits select event with the full ThirdParty object on selection', async () => {
    const w = factory();
    await w.find('input').setValue('Maers');
    const items = w.findAll('.ta-item');
    await items[0].trigger('mousedown');
    const events = w.emitted('select');
    expect(events).toBeTruthy();
    expect(events![0][0]).toMatchObject({ companyName: 'Maersk', industryType: 'Shipping Line' });
  });

  it('shows a validation error on blur when required and value is not in the registry', async () => {
    const w = factory({ required: true, modelValue: 'Unknown Co' });
    await w.find('input').trigger('blur');
    // wait for the 120ms blur timer of TypeaheadInput
    await new Promise((r) => setTimeout(r, 150));
    expect(w.text()).toContain('Unknown third party');
  });

  it('does not show error when value matches the registry', async () => {
    const w = factory({ required: true, modelValue: 'Maersk' });
    await w.find('input').trigger('blur');
    await new Promise((r) => setTimeout(r, 150));
    expect(w.text()).not.toContain('Unknown third party');
  });

  it('shows the "+ create" footer for admins when nothing matches the typed text', async () => {
    // re-mock to admin=true
    vi.doMock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => true }) }));
    vi.resetModules();
    const { default: Comp } = await import('../src/components/ui/ThirdPartyAutocomplete.vue');
    const w = mount(Comp, {
      global: { plugins: [i18n] },
      props: { modelValue: 'NewCo', label: 'Operator', industryType: 'Shipping Line' },
    });
    await w.find('input').trigger('focus');
    expect(w.text()).toContain('Create new third party');
    expect(w.text()).toContain('NewCo');
  });

  it('does not show the "+ create" footer for non-admins', async () => {
    const w = factory({ modelValue: 'NewCo' });
    await w.find('input').trigger('focus');
    expect(w.text()).not.toContain('Create new third party');
  });

  it('opens the create modal when the footer is clicked and selects the new tier after save', async () => {
    vi.doMock('../src/stores/authStore', () => ({ useAuthStore: () => ({ isAdmin: () => true }) }));
    const createMinimal = vi.fn().mockResolvedValue({
      id: '99', companyName: 'NewCo', industryType: 'Shipping Line', companyAddress: 'X',
    });
    vi.doMock('../src/composables/use.third-party', () => ({
      useThirdParty: () => ({ thirdParties: ref([...tps]), createMinimal }),
    }));
    vi.resetModules();
    const { default: Comp } = await import('../src/components/ui/ThirdPartyAutocomplete.vue');

    const w = mount(Comp, {
      global: { plugins: [i18n] },
      props: { modelValue: 'NewCo', label: 'Operator', industryType: 'Shipping Line' },
    });
    await w.find('input').trigger('focus');
    await w.find('button').trigger('click');           // "+ create" footer

    // modal is open: fill address and submit
    await w.find('input[name="companyAddress"]').setValue('Some Address');
    await w.find('form').trigger('submit.prevent');
    await flushPromises();

    // parent received update:modelValue and select
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual(['NewCo']);
    expect(w.emitted('select')?.at(-1)?.[0]).toMatchObject({ id: '99', companyName: 'NewCo' });
  });
});
