import api from '../plugin/axios'
import type { SupportedLocale } from '../i18n/locales'

export interface TranslationDoc {
  id: string
  locale: SupportedLocale
  key: string
  value: string
  updatedAt: string
  updatedBy: string | null
}

export async function getMessages(locale: SupportedLocale): Promise<Record<string, string>> {
  const res = await api.get(`/i18n/${locale}`)
  return res.data ?? {}
}

export async function getVersion(locale: SupportedLocale): Promise<string> {
  const res = await api.get(`/i18n/${locale}/version`)
  return res.data?.version ?? ''
}

export async function putEntry(
  locale: SupportedLocale,
  key: string,
  value: string,
): Promise<TranslationDoc | null> {
  const res = await api.put(`/i18n/${locale}/${encodeURIComponent(key)}`, { value })
  return res.status === 204 ? null : res.data
}

export async function deleteEntry(locale: SupportedLocale, key: string): Promise<void> {
  await api.delete(`/i18n/${locale}/${encodeURIComponent(key)}`)
}

export async function bulkImport(
  locale: SupportedLocale,
  entries: Record<string, string>,
): Promise<number> {
  const res = await api.post(`/i18n/${locale}/import`, entries)
  return res.data?.updated ?? 0
}

export async function exportBlob(locale: SupportedLocale): Promise<Blob> {
  const res = await api.get(`/i18n/${locale}/export`, { responseType: 'blob' })
  return res.data
}
