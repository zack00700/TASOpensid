import { createI18n } from 'vue-i18n'
import en from '../locales/en.json'
import { isSupportedLocale, type SupportedLocale } from './locales'

const STORAGE_KEY = 'locale'

function readStoredLocale(): SupportedLocale {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return isSupportedLocale(v) ? v : 'en'
  } catch {
    return 'en'
  }
}

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale(),
  fallbackLocale: 'en',
  messages: { en },
  missingWarn: import.meta.env.DEV,
  fallbackWarn: false,
})
