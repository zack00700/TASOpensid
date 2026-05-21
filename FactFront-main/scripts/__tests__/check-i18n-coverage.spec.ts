import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { resolve } from 'node:path'
import { mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { findUsedKeys, missingKeys } from '../check-i18n-coverage'

const TMP = resolve(tmpdir(), 'i18n-coverage-test')

beforeEach(() => {
  if (existsSync(TMP)) rmSync(TMP, { recursive: true, force: true })
  mkdirSync(TMP, { recursive: true })
})
afterEach(() => {
  if (existsSync(TMP)) rmSync(TMP, { recursive: true, force: true })
})

function writeFile(rel: string, content: string) {
  const path = resolve(TMP, rel)
  mkdirSync(resolve(path, '..'), { recursive: true })
  writeFileSync(path, content, 'utf-8')
}

describe('check-i18n-coverage', () => {
  it('finds keys from $t and t() calls in templates and scripts', () => {
    writeFile('src/components/Foo.vue', `
      <script setup>
      import { useI18n } from 'vue-i18n'
      const { t } = useI18n()
      const a = t('foo.bar')
      const b = t("foo.baz")
      </script>
      <template>
        <div>{{ $t('foo.title') }}</div>
        <button :title="t('common.close')">X</button>
      </template>
    `)
    const keys = findUsedKeys(TMP)
    expect(keys.has('foo.bar')).toBe(true)
    expect(keys.has('foo.baz')).toBe(true)
    expect(keys.has('foo.title')).toBe(true)
    expect(keys.has('common.close')).toBe(true)
  })

  it('returns empty when no t() calls are present', () => {
    writeFile('src/components/Plain.vue', `<template><div>Hi</div></template>`)
    const keys = findUsedKeys(TMP)
    expect(keys.size).toBe(0)
  })

  it('reports missing keys', () => {
    writeFile('src/components/Foo.vue', `<template>{{ $t('foo.title') }}{{ $t('foo.subtitle') }}</template>`)
    writeFile('src/locales/en.json', JSON.stringify({ 'foo.title': 'Title' }))
    const missing = missingKeys(TMP)
    expect(missing).toEqual(['foo.subtitle'])
  })

  it('reports nothing when coverage is complete', () => {
    writeFile('src/components/Foo.vue', `<template>{{ $t('foo.title') }}</template>`)
    writeFile('src/locales/en.json', JSON.stringify({ 'foo.title': 'Title' }))
    const missing = missingKeys(TMP)
    expect(missing).toEqual([])
  })
})
