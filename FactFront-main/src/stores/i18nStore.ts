import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import { i18n } from '../i18n'
import {
  SUPPORTED_LOCALES,
  type SupportedLocale,
  isSupportedLocale,
} from '../i18n/locales'
import * as i18nApi from '../services/i18nApi'

interface Cached {
  version: string
  messages: Record<string, string>
}

function cacheKey(loc: SupportedLocale) {
  return `i18n:${loc}`
}

function readCache(loc: SupportedLocale): Cached | null {
  try {
    const raw = localStorage.getItem(cacheKey(loc))
    if (!raw) return null
    const parsed = JSON.parse(raw) as Cached
    if (typeof parsed?.version === 'string' && parsed.messages) return parsed
    return null
  } catch {
    return null
  }
}

function writeCache(loc: SupportedLocale, cached: Cached) {
  try {
    localStorage.setItem(cacheKey(loc), JSON.stringify(cached))
  } catch {
    /* private mode etc. */
  }
}

function readStoredLocale(): SupportedLocale {
  try {
    const v = localStorage.getItem('locale')
    return isSupportedLocale(v) ? v : 'en'
  } catch {
    return 'en'
  }
}

export const useI18nStore = defineStore('i18n', () => {
  const currentLocale: Ref<SupportedLocale> = ref(readStoredLocale())
  const loadedLocales: Ref<Set<SupportedLocale>> = ref(new Set(['en']))
  const versions: Ref<Partial<Record<SupportedLocale, string>>> = ref({})

  async function loadLocale(loc: SupportedLocale): Promise<void> {
    if (loc === 'en') {
      loadedLocales.value.add('en')
      return
    }
    const cached = readCache(loc)
    let serverVersion = ''
    try {
      serverVersion = await i18nApi.getVersion(loc)
    } catch {
      // Backend down: fall back to cache if any
    }

    if (cached && cached.version === serverVersion && serverVersion !== '') {
      i18n.global.setLocaleMessage(loc, cached.messages)
      versions.value[loc] = cached.version
      loadedLocales.value.add(loc)
      return
    }

    const messages = await i18nApi.getMessages(loc)
    i18n.global.setLocaleMessage(loc, messages)
    versions.value[loc] = serverVersion
    writeCache(loc, { version: serverVersion, messages })
    loadedLocales.value.add(loc)
  }

  async function setLocale(loc: SupportedLocale): Promise<void> {
    if (!loadedLocales.value.has(loc)) {
      await loadLocale(loc)
    }
    i18n.global.locale.value = loc
    currentLocale.value = loc
    try {
      localStorage.setItem('locale', loc)
    } catch {
      /* private mode */
    }
  }

  async function refreshLocale(loc: SupportedLocale): Promise<void> {
    if (loc === 'en') return
    const serverVersion = await i18nApi.getVersion(loc)
    if (serverVersion !== versions.value[loc]) {
      const messages = await i18nApi.getMessages(loc)
      i18n.global.setLocaleMessage(loc, messages)
      versions.value[loc] = serverVersion
      writeCache(loc, { version: serverVersion, messages })
    }
  }

  // ── Admin actions ──────────────────────────────────────────────────────────

  interface Entry {
    key: string
    source: string
    target: string
    translated: boolean
  }

  function entries(targetLocale: SupportedLocale): Entry[] {
    const en = i18n.global.getLocaleMessage<Record<string, string>>('en')
    const tgt = i18n.global.getLocaleMessage<Record<string, string>>(targetLocale)
    return Object.keys(en).sort().map((key) => {
      const target = tgt && tgt[key] ? tgt[key] : ''
      return { key, source: en[key], target, translated: target !== '' }
    })
  }

  async function saveEntry(
    targetLocale: SupportedLocale,
    key: string,
    value: string,
  ): Promise<void> {
    if (value === '') {
      await i18nApi.deleteEntry(targetLocale, key)
      const msgs = { ...i18n.global.getLocaleMessage<Record<string, string>>(targetLocale) }
      delete msgs[key]
      i18n.global.setLocaleMessage(targetLocale, msgs)
    } else {
      await i18nApi.putEntry(targetLocale, key, value)
      const msgs = { ...i18n.global.getLocaleMessage<Record<string, string>>(targetLocale) }
      msgs[key] = value
      i18n.global.setLocaleMessage(targetLocale, msgs)
    }
    const now = new Date().toISOString()
    versions.value[targetLocale] = now
    const cached = readCache(targetLocale)
    if (cached) {
      writeCache(targetLocale, {
        version: now,
        messages: i18n.global.getLocaleMessage<Record<string, string>>(targetLocale),
      })
    }
  }

  async function importJson(
    targetLocale: SupportedLocale,
    json: Record<string, string>,
  ): Promise<number> {
    const updated = await i18nApi.bulkImport(targetLocale, json)
    await refreshLocale(targetLocale)
    return updated
  }

  async function exportJson(targetLocale: SupportedLocale): Promise<void> {
    const blob = await i18nApi.exportBlob(targetLocale)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${targetLocale}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  return {
    currentLocale,
    loadedLocales,
    versions,
    setLocale,
    loadLocale,
    refreshLocale,
    entries,
    saveEntry,
    importJson,
    exportJson,
    SUPPORTED_LOCALES,
  }
})
