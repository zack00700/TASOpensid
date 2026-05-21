export const SUPPORTED_LOCALES = ['en', 'fr', 'es'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

export const NATIVE_NAMES: Record<SupportedLocale, string> = {
  en: 'English',
  fr: 'Français',
  es: 'Español',
}

export function isSupportedLocale(value: unknown): value is SupportedLocale {
  return typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}
