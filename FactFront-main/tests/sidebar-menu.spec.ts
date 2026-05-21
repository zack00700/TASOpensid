import { mount } from '@vue/test-utils';
import { describe, it, expect } from 'vitest';
import SidebarMenu from '../src/components/SidebarMenu.vue';
import routerPlugin, { useRouter } from '../src/router';
import { i18n } from '../src/i18n';

async function setup(clear = true) {
  if (clear) localStorage.clear();
  const router = useRouter();
  router.push('/invoices');
  const wrapper = mount(SidebarMenu, {
    global: {
      plugins: [routerPlugin, i18n],
    },
  });
  return { wrapper, router };
}

describe('SidebarMenu', () => {
  it('navigates to Translations when clicked', async () => {
    const { wrapper, router } = await setup();
    const cfgHeader = wrapper.find('[data-test="section-configuration"]');
    await cfgHeader.trigger('click');
    await wrapper.find('[data-test="menu-item-translations"]').trigger('click');
    expect(router.currentRoute.value.path).toBe('/i18n');
  });

  it('collapses a section when its header is clicked', async () => {
    const { wrapper } = await setup();
    const header = wrapper.find('[data-test="section-operations"]');
    await header.trigger('click'); // collapse
    await wrapper.vm.$nextTick();
    await new Promise((r) => setTimeout(r));
    await wrapper.vm.$nextTick();
    const list = wrapper.find('#section-operations-list');
    expect(list.classes()).toContain('max-h-0');
  });

  it('persists collapsed state across reloads', async () => {
    const { wrapper } = await setup();
    const header = wrapper.find('[data-test="section-operations"]');
    await header.trigger('click'); // collapse
    await wrapper.vm.$nextTick();
    wrapper.unmount();
    const { wrapper: wrapper2 } = await setup(false);
    const list2 = wrapper2.find('#section-operations-list');
    expect(list2.classes()).toContain('max-h-0');
  });

  it('renders icons in section headers', async () => {
    const { wrapper } = await setup();
    const opsHeader = wrapper.find('[data-test="section-operations"]');
    const cfgHeader = wrapper.find('[data-test="section-configuration"]');
    expect(opsHeader.findAll('svg').length).toBeGreaterThan(1);
    expect(cfgHeader.findAll('svg').length).toBeGreaterThan(1);
  });

  it('includes Translations in the configuration menu', async () => {
    const { wrapper } = await setup();
    const cfgHeader = wrapper.find('[data-test="section-configuration"]');
    await cfgHeader.trigger('click');
    expect(wrapper.find('[data-test="menu-item-translations"]').exists()).toBe(true);
  });
});
