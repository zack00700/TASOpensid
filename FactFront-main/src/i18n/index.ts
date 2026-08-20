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

/**
 * Translations are a flat key -> string map, filled at runtime by i18nStore
 * (which fetches each locale from the backend and calls setLocaleMessage).
 *
 * Stating the schema explicitly matters: left to inference, vue-i18n derives it
 * from the `en.json` literal — a 2005-key object type — and pins the locale type
 * to 'en'. Every later setLocaleMessage(locale, Record<string, string>) and
 * `locale.value = 'fr'` then fails to typecheck against those frozen literals.
 */
export type MessageSchema = Record<string, string>

export const i18n = createI18n({
  legacy: false,
  locale: readStoredLocale() as SupportedLocale,
  fallbackLocale: 'en',
  messages: { en: en as MessageSchema } as Record<SupportedLocale, MessageSchema>,
  missingWarn: import.meta.env.DEV,
  fallbackWarn: false,
})
