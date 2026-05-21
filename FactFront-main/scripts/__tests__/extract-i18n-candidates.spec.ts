import { describe, it, expect } from 'vitest'
import { extract } from '../extract-i18n-candidates'
import { resolve } from 'node:path'

const FIXTURE = resolve(__dirname, 'fixtures/sample.vue')

describe('extract-i18n-candidates', () => {
  it('returns the file path and a candidates array', () => {
    const m = extract(FIXTURE)
    expect(m.file).toBe(FIXTURE)
    expect(Array.isArray(m.candidates)).toBe(true)
    expect(m.candidates.length).toBeGreaterThan(0)
  })

  it('detects text between tags', () => {
    const m = extract(FIXTURE)
    const sources = m.candidates.map((c) => c.source)
    expect(sources).toContain('Welcome back')
    expect(sources).toContain('Save')
  })

  it('detects placeholder, aria-label, title attrs', () => {
    const m = extract(FIXTURE)
    const byKind = (k: string) => m.candidates.filter((c) => c.kind === k).map((c) => c.source)
    expect(byKind('attr.placeholder')).toContain('Search…')
    expect(byKind('attr.ariaLabel')).toContain('Close')
    expect(byKind('attr.title')).toContain('Save changes')
  })

  it('detects confirm() and toast text', () => {
    const m = extract(FIXTURE)
    expect(m.candidates.some((c) => c.kind === 'confirm' && c.source === 'Delete this row?')).toBe(true)
    expect(m.candidates.some((c) => c.kind === 'toast' && c.source === 'Saved successfully')).toBe(true)
  })

  it('flags interpolated text', () => {
    const m = extract(FIXTURE)
    const interpolated = m.candidates.find((c) => c.kind === 'text.interpolated')
    expect(interpolated?.source).toContain('You have')
  })

  it('skips strings inside <style>, comments, and console.log', () => {
    const m = extract(FIXTURE)
    const sources = m.candidates.map((c) => c.source)
    expect(sources).not.toContain('do not extract from styles')
    expect(sources).not.toContain('I am a comment with English text inside')
    expect(sources).not.toContain('debug only')
    expect(sources).not.toContain('should-not-extract')
  })

  it('suggests keys based on the file basename', () => {
    const m = extract(FIXTURE)
    const save = m.candidates.find((c) => c.source === 'Save' && c.kind === 'text')
    expect(save?.suggestedKey).toMatch(/^sample\./)
  })

  it('promotes short generic aria-label values to common.*', () => {
    const m = extract(FIXTURE)
    const close = m.candidates.find((c) => c.kind === 'attr.ariaLabel' && c.source === 'Close')
    expect(close?.suggestedKey).toBe('common.close')
  })

  it('marks alreadyKey when a value already exists in en.json', async () => {
    const path = resolve(process.cwd(), 'src/locales/en.json')
    const fs = await import('node:fs')
    const original = fs.readFileSync(path, 'utf-8')
    fs.writeFileSync(path, JSON.stringify({ 'common.save': 'Save' }))
    try {
      const m = extract(FIXTURE)
      const save = m.candidates.find((c) => c.source === 'Save' && c.kind === 'text')
      expect(save?.alreadyKey).toBe('common.save')
    } finally {
      fs.writeFileSync(path, original)
    }
  })

  it('does not re-emit already-migrated bindings ($t / t() inside attrs and templates)', () => {
    const fs = require('node:fs') as typeof import('node:fs')
    const tmp = require('node:os').tmpdir() + '/extract-migrated-test.vue'
    fs.writeFileSync(tmp, `
      <script setup>
      import { useI18n } from 'vue-i18n'
      const { t } = useI18n()
      </script>
      <template>
        <div :aria-label="$t('foo.bar')">
          {{ $t('foo.title') }}
        </div>
        <button :title="t('common.close')">x</button>
      </template>
    `)
    try {
      const m = extract(tmp)
      // No source should be the literal "$t(" or "t(" or similar fragments.
      const fragments = m.candidates.filter((c) => /^\$?t\s*\(/.test(c.source))
      expect(fragments).toEqual([])
    } finally {
      fs.unlinkSync(tmp)
    }
  })
})
