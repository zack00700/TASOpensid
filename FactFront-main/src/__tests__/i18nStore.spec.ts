import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '../stores/i18nStore'
import * as api from '../services/i18nApi'
import { i18n } from '../i18n'

vi.mock('../services/i18nApi')

describe('i18nStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    i18n.global.locale.value = 'en'
    vi.resetAllMocks()
  })

  it('starts with locale from localStorage or "en"', () => {
    const store = useI18nStore()
    expect(store.currentLocale).toBe('en')
  })

  it('switching to en is synchronous and does not call API', async () => {
    const store = useI18nStore()
    const spy = vi.mocked(api.getMessages)
    await store.setLocale('en')
    expect(spy).not.toHaveBeenCalled()
    expect(store.currentLocale).toBe('en')
    expect(localStorage.getItem('locale')).toBe('en')
  })

  it('switching to fr fetches messages, injects, persists', async () => {
    vi.mocked(api.getMessages).mockResolvedValue({ 'common.save': 'Sauvegarder' })
    vi.mocked(api.getVersion).mockResolvedValue('2026-05-03T18:00:00Z')
    const store = useI18nStore()
    await store.setLocale('fr')
    expect(api.getMessages).toHaveBeenCalledWith('fr')
    expect(store.currentLocale).toBe('fr')
    const msgs = i18n.global.getLocaleMessage<Record<string, string>>('fr')
    expect(msgs['common.save']).toBe('Sauvegarder')
    // Les traductions embarquées (fr.json) survivent à la réponse du serveur :
    // celui-ci surcharge clé par clé au lieu de remplacer tout le jeu.
    expect(Object.keys(msgs).length).toBeGreaterThan(1)
    expect(msgs['common.cancel']).toBeDefined()
    expect(localStorage.getItem('locale')).toBe('fr')
  })

  it('uses cached messages when version unchanged', async () => {
    localStorage.setItem(
      'i18n:fr',
      JSON.stringify({ version: 'v1', messages: { 'common.cancel': 'Annuler' } }),
    )
    vi.mocked(api.getVersion).mockResolvedValue('v1')
    const store = useI18nStore()
    await store.setLocale('fr')
    expect(api.getMessages).not.toHaveBeenCalled()
    expect(i18n.global.getLocaleMessage<Record<string, string>>('fr')['common.cancel']).toBe('Annuler')
  })

  it('refetches when server version is newer', async () => {
    localStorage.setItem(
      'i18n:fr',
      JSON.stringify({ version: 'v1', messages: { 'common.cancel': 'Annuler' } }),
    )
    vi.mocked(api.getVersion).mockResolvedValue('v2')
    vi.mocked(api.getMessages).mockResolvedValue({ 'common.cancel': 'Annuler v2' })
    const store = useI18nStore()
    await store.setLocale('fr')
    expect(api.getMessages).toHaveBeenCalled()
    expect(i18n.global.getLocaleMessage<Record<string, string>>('fr')['common.cancel']).toBe('Annuler v2')
  })

  it('keeps bundled translations when the backend returns nothing', async () => {
    // Regression : sur une base de traductions vide, setLocaleMessage(loc, {})
    // effaçait fr.json et l'interface retombait en anglais.
    vi.mocked(api.getVersion).mockResolvedValue('')
    vi.mocked(api.getMessages).mockResolvedValue({})
    const store = useI18nStore()
    await store.setLocale('fr')
    const msgs = i18n.global.getLocaleMessage<Record<string, string>>('fr')
    expect(Object.keys(msgs).length).toBeGreaterThan(1000)
    expect(store.currentLocale).toBe('fr')
  })

  it('saveEntry calls API and updates the local message', async () => {
    vi.mocked(api.putEntry).mockResolvedValue({
      id: 'fr:common.save',
      locale: 'fr',
      key: 'common.save',
      value: 'Sauvegarder',
      updatedAt: '2026-05-03T18:00:00Z',
      updatedBy: 'me',
    })
    const store = useI18nStore()
    await store.saveEntry('fr', 'common.save', 'Sauvegarder')
    expect(api.putEntry).toHaveBeenCalledWith('fr', 'common.save', 'Sauvegarder')
    expect(i18n.global.getLocaleMessage('fr')).toMatchObject({ 'common.save': 'Sauvegarder' })
  })

  it('saveEntry with empty value deletes', async () => {
    i18n.global.setLocaleMessage('fr', { 'common.save': 'Sauvegarder' })
    vi.mocked(api.deleteEntry).mockResolvedValue()
    const store = useI18nStore()
    await store.saveEntry('fr', 'common.save', '')
    expect(api.deleteEntry).toHaveBeenCalledWith('fr', 'common.save')
    const msgs = i18n.global.getLocaleMessage<Record<string, string>>('fr')
    expect(msgs['common.save']).toBeUndefined()
  })
})
