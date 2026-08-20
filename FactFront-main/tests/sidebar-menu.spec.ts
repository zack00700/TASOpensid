import { mount } from '@vue/test-utils';
import { describe, it, expect } from 'vitest';
import SidebarMenu from '../src/components/SidebarMenu.vue';
import router from '../src/router';
import { i18n } from '../src/i18n';
import { useAuthStore } from '../src/stores/authStore';

/**
 * Waits for the router to settle on `path`. Routes load their component through
 * a dynamic import(), so a navigation started by a click needs more than one
 * macrotask to complete and a fixed sleep is either flaky or needlessly slow.
 */
async function waitForRoute(path: string, timeoutMs = 1000) {
  const deadline = Date.now() + timeoutMs;
  while (router.currentRoute.value.path !== path && Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 5));
  }
  return router.currentRoute.value.path;
}

async function setup(clear = true) {
  if (clear) localStorage.clear();
  // Every app route is behind `meta.requiresAuth`, and the router guard cancels
  // the navigation when the auth store has no token — which is the default for
  // the fresh Pinia that tests/setup.ts installs. Seed a token so the guard lets
  // the navigation through; `isAuthenticated` accepts any non-JWT string.
  useAuthStore().token = 'test-token';
  router.push('/invoices');
  await router.isReady();
  const wrapper = mount(SidebarMenu, {
    global: {
      plugins: [router, i18n],
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
    expect(await waitForRoute('/i18n')).toBe('/i18n');
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
