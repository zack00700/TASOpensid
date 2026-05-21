import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ThirdPartyHistory from '../src/components/ThirdPartyHistory.vue';
import { i18n } from '../src/i18n';

const historyData = [
  {
    id: '1',
    version: 1,
    updatedAt: '2024-01-01',
    data: { fullName: 'Alice', nested: { level: 'one' } },
  },
  {
    id: '2',
    version: 2,
    updatedAt: '2024-01-02',
    data: { fullName: 'Alice B', nested: { level: 'two' }, extra: 'field' },
  },
];

describe('ThirdPartyHistory', () => {
  it('renders many diff rows', async () => {
    const wrapper = mount(ThirdPartyHistory, {
      props: { id: 'x' },
      global: {
        plugins: [i18n],
        provide: { $axios: { get: () => ({ data: historyData }) } },
      },
    });
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('Version 2');
    const rows = wrapper.findAll('tbody tr');
    expect(rows.length).toBeGreaterThan(0);
  });

  it('handles deeply nested diffs', async () => {
    const wrapper = mount(ThirdPartyHistory, {
      props: { id: 'x' },
      global: { plugins: [i18n], provide: { $axios: { get: () => ({ data: historyData }) } } },
    });
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();
    expect(wrapper.html()).toContain('nested.level');
  });
});

