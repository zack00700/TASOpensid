import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DatetimeInput from '../src/components/ui/DatetimeInput.vue';
import { i18n } from '../src/i18n';

const mountOpts = { global: { plugins: [i18n] } };

describe('DatetimeInput', () => {
  it('renders the value passed via modelValue and a Now button', () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '2026-05-23T14:30' },
    });
    const input = wrapper.find<HTMLInputElement>('input[type="datetime-local"]');
    expect(input.exists()).toBe(true);
    expect(input.element.value).toBe('2026-05-23T14:30');
    expect(wrapper.find('button').text()).toBe('Now');
  });

  it('emits update:modelValue on input change', async () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '' },
    });
    const input = wrapper.find<HTMLInputElement>('input[type="datetime-local"]');
    await input.setValue('2026-05-23T09:00');
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2026-05-23T09:00']);
  });

  it('Now button emits update:modelValue in YYYY-MM-DDTHH:MM local form', async () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '' },
    });
    await wrapper.find('button').trigger('click');
    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as string;
    expect(emitted).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);

    // The emitted value should reflect *local* time (Y-M-D-H-M from new Date()),
    // not UTC. Compare to a freshly-computed local value.
    const localNow = new Date();
    const expectedYearMonthDay =
      `${localNow.getFullYear()}-${String(localNow.getMonth() + 1).padStart(2, '0')}-${String(localNow.getDate()).padStart(2, '0')}`;
    expect(emitted.slice(0, 10)).toBe(expectedYearMonthDay);
  });

  it('forwards data-test to the input and derives "-now" for the button', () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '' },
      attrs: { 'data-test': 'visit-eta' },
    });
    const input = wrapper.find('input[type="datetime-local"]');
    const button = wrapper.find('button');
    expect(input.attributes('data-test')).toBe('visit-eta');
    expect(button.attributes('data-test')).toBe('visit-eta-now');
  });

  it('disabled prop disables both input and Now button', async () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '', disabled: true },
    });
    expect(wrapper.find<HTMLInputElement>('input').element.disabled).toBe(true);
    expect(wrapper.find<HTMLButtonElement>('button').element.disabled).toBe(true);

    // Click on disabled button must not emit
    await wrapper.find('button').trigger('click');
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
  });

  it('honours the min prop for the underlying input', () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: '', min: '2026-05-23T08:00' },
    });
    expect(wrapper.find('input').attributes('min')).toBe('2026-05-23T08:00');
  });

  it('coerces null/undefined modelValue to empty string in the input', () => {
    const wrapper = mount(DatetimeInput, {
      ...mountOpts,
      props: { modelValue: null },
    });
    expect(wrapper.find<HTMLInputElement>('input').element.value).toBe('');
  });
});
