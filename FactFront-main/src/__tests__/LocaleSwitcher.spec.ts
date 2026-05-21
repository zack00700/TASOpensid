import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'
import { useI18nStore } from '../stores/i18nStore'

describe('LocaleSwitcher', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows current locale code in the trigger', () => {
    const wrapper = mount(LocaleSwitcher)
    expect(wrapper.find('button').text()).toContain('EN')
  })

  it('lists all supported locales when opened', async () => {
    const wrapper = mount(LocaleSwitcher)
    await wrapper.find('button').trigger('click')
    const items = wrapper.findAll('[role="menuitem"]')
    const labels = items.map((i) => i.text())
    expect(labels.some((l) => l.includes('English'))).toBe(true)
    expect(labels.some((l) => l.includes('Français'))).toBe(true)
    expect(labels.some((l) => l.includes('Español'))).toBe(true)
  })

  it('marks the active locale with a check', async () => {
    const wrapper = mount(LocaleSwitcher)
    await wrapper.find('button').trigger('click')
    const active = wrapper
      .findAll('[role="menuitem"]')
      .find((i) => i.text().includes('English'))!
    expect(active.text()).toContain('✓')
  })

  it('calls setLocale on click', async () => {
    const store = useI18nStore()
    const spy = vi.spyOn(store, 'setLocale').mockResolvedValue()
    const wrapper = mount(LocaleSwitcher)
    await wrapper.find('button').trigger('click')
    const fr = wrapper
      .findAll('[role="menuitem"]')
      .find((i) => i.text().includes('Français'))!
    await fr.trigger('click')
    expect(spy).toHaveBeenCalledWith('fr')
  })
})
