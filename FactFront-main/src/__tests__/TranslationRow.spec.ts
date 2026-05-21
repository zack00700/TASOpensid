import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TranslationRow from '../components/i18n/TranslationRow.vue'

const baseEntry = { key: 'common.save', source: 'Save', target: '', translated: false }

describe('TranslationRow', () => {
  it('renders key, source, and current target', () => {
    const wrapper = mount(TranslationRow, {
      props: { entry: { ...baseEntry, target: 'Sauvegarder', translated: true } },
    })
    expect(wrapper.text()).toContain('common.save')
    expect(wrapper.text()).toContain('Save')
    expect((wrapper.find('input').element as HTMLInputElement).value).toBe('Sauvegarder')
  })

  it('marks untranslated rows', () => {
    const wrapper = mount(TranslationRow, { props: { entry: baseEntry } })
    expect(wrapper.find('[data-untranslated]').exists()).toBe(true)
  })

  it('emits save with new value on blur', async () => {
    const wrapper = mount(TranslationRow, { props: { entry: baseEntry } })
    const input = wrapper.find('input')
    await input.setValue('Sauvegarder')
    await input.trigger('blur')
    expect(wrapper.emitted('save')).toBeTruthy()
    expect(wrapper.emitted('save')![0]).toEqual(['common.save', 'Sauvegarder'])
  })

  it('emits save on Enter', async () => {
    const wrapper = mount(TranslationRow, { props: { entry: baseEntry } })
    const input = wrapper.find('input')
    await input.setValue('Annuler')
    await input.trigger('keydown.enter')
    expect(wrapper.emitted('save')).toBeTruthy()
  })

  it('reverts on Esc and does not emit save', async () => {
    const wrapper = mount(TranslationRow, {
      props: { entry: { ...baseEntry, target: 'Sauvegarder', translated: true } },
    })
    const input = wrapper.find('input')
    await input.setValue('CHANGED')
    await input.trigger('keydown.esc')
    await input.trigger('blur')
    expect(wrapper.emitted('save')).toBeFalsy()
    expect((input.element as HTMLInputElement).value).toBe('Sauvegarder')
  })

  it('does not emit save when value unchanged', async () => {
    const wrapper = mount(TranslationRow, {
      props: { entry: { ...baseEntry, target: 'Save it', translated: true } },
    })
    const input = wrapper.find('input')
    await input.trigger('blur')
    expect(wrapper.emitted('save')).toBeFalsy()
  })
})
