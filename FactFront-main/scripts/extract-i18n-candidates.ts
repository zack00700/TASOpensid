#!/usr/bin/env bun
/**
 * Extract translation candidates from a Vue SFC.
 *
 * Usage:  bun run scripts/extract-i18n-candidates.ts <file.vue>
 *
 * Output: JSON manifest on stdout. The output is a starting point for an
 * implementer subagent — false positives are acceptable and expected.
 */
import { readFileSync, existsSync, realpathSync } from 'node:fs'
import { basename, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export interface Candidate {
  kind: string
  source: string
  location: string
  suggestedKey: string
  alreadyKey: string | null
}

export interface Manifest {
  file: string
  candidates: Candidate[]
}

const ATTR_NAMES = [
  'placeholder',
  'title',
  'aria-label',
  'aria-description',
  'alt',
] as const

const COMMON_GENERICS = new Set([
  'close', 'open', 'save', 'cancel', 'edit', 'delete', 'remove', 'add',
  'create', 'update', 'submit', 'reset', 'search', 'loading', 'confirm',
  'yes', 'no', 'ok', 'back', 'next', 'previous', 'done',
])

function pageNamespace(filePath: string): string {
  const base = basename(filePath, '.vue')
  return base.charAt(0).toLowerCase() + base.slice(1)
}

function camelSlug(text: string): string {
  const words = text.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().split(/\s+/)
  if (words.length === 0 || words[0] === '') return 'unnamed'
  const slug = words
    .map((w, i) => (i === 0 ? w : w.charAt(0).toUpperCase() + w.slice(1)))
    .join('')
  return slug.slice(0, 40) || 'unnamed'
}

function loadEnJson(): Record<string, string> {
  const path = resolve(process.cwd(), 'src/locales/en.json')
  if (!existsSync(path)) return {}
  try {
    return JSON.parse(readFileSync(path, 'utf-8'))
  } catch {
    return {}
  }
}

function findAlready(source: string, en: Record<string, string>): string | null {
  for (const [k, v] of Object.entries(en)) {
    if (v === source) return k
  }
  return null
}

function isTranslatable(s: string): boolean {
  const trimmed = s.trim()
  if (trimmed.length < 2) return false
  if (!/[a-zA-Z]/.test(trimmed)) return false
  if (/^\{\{[\s\S]*\}\}$/.test(trimmed)) return false
  if (/^[\s\d.,!?:;\-_*+/()\[\]]*$/.test(trimmed)) return false
  return true
}

function locationFromIndex(content: string, idx: number): string {
  let line = 1
  let col = 1
  for (let i = 0; i < idx; i++) {
    if (content[i] === '\n') { line++; col = 1 } else { col++ }
  }
  return `${line}:${col}`
}

/** Replace block contents with spaces (same length) so indices stay aligned. */
function blank(src: string, start: number, end: number): string {
  const region = src.slice(start, end)
  const blanked = region.replace(/[^\n]/g, ' ')
  return src.slice(0, start) + blanked + src.slice(end)
}

function stripStyleBlocks(src: string): string {
  let out = src
  const re = /<style[^>]*>[\s\S]*?<\/style>/gi
  let m: RegExpExecArray | null
  while ((m = re.exec(out)) !== null) {
    out = blank(out, m.index, m.index + m[0].length)
  }
  return out
}

function stripComments(src: string): string {
  let out = src
  const re1 = /<!--[\s\S]*?-->/g
  let m: RegExpExecArray | null
  while ((m = re1.exec(out)) !== null) {
    out = blank(out, m.index, m.index + m[0].length)
  }
  const re2 = /\/\*[\s\S]*?\*\//g
  while ((m = re2.exec(out)) !== null) {
    out = blank(out, m.index, m.index + m[0].length)
  }
  out = out.replace(/(^|[^:\w])\/\/.*$/gm, (whole, prefix) => prefix + ' '.repeat(whole.length - prefix.length))
  return out
}

interface Block { content: string; offset: number }

function extractBlock(src: string, tag: 'template' | 'script'): Block {
  const re = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i')
  const m = re.exec(src)
  if (!m) return { content: '', offset: 0 }
  const inner = m[1]
  const innerOffset = m.index + m[0].indexOf(inner)
  return { content: inner, offset: innerOffset }
}

function findTagText(template: string, fileOffset: number): {
  source: string; index: number; interpolated: boolean
}[] {
  const out: { source: string; index: number; interpolated: boolean }[] = []
  const re = />[\t ]*([^<]+?)[\t ]*</g
  let m: RegExpExecArray | null
  while ((m = re.exec(template)) !== null) {
    const raw = m[1]
    if (!raw || !raw.trim()) continue
    if (!isTranslatable(raw)) continue
    if (/\{\{\s*\$?t\s*\(/.test(raw)) continue // already migrated, don't re-emit
    if (/=>|\}\s*["'`]/.test(raw)) continue // arrow function or JS expression bleed (event handler)
    const interpolated = /\{\{[\s\S]*?\}\}/.test(raw)
    if (interpolated) {
      const stripped = raw.replace(/\{\{[\s\S]*?\}\}/g, '').trim()
      if (!isTranslatable(stripped)) continue
    }
    const localIdx = m.index + m[0].indexOf(raw)
    out.push({ source: raw.replace(/\s+/g, ' ').trim(), index: fileOffset + localIdx, interpolated })
  }
  return out
}

function findAttrValues(template: string, fileOffset: number): {
  attr: string; source: string; index: number
}[] {
  const out: { attr: string; source: string; index: number }[] = []
  for (const attr of ATTR_NAMES) {
    const re = new RegExp(`\\b${attr}=(?:"([^"]*)"|'([^']*)')`, 'g')
    let m: RegExpExecArray | null
    while ((m = re.exec(template)) !== null) {
      const value = m[1] !== undefined ? m[1] : m[2]
      if (!value) continue
      if (/\$?t\s*\(\s*['"`]/.test(value)) continue // contains t()/$t() call → already migrated (covers ternaries/concats)
      if (!isTranslatable(value)) continue
      const localIdx = m.index + m[0].indexOf(value)
      out.push({ attr, source: value, index: fileOffset + localIdx })
    }
  }
  return out
}

function findDialogCalls(script: string, fileOffset: number): {
  fn: string; source: string; index: number
}[] {
  const out: { fn: string; source: string; index: number }[] = []
  const re = /\b(confirm|alert|prompt)\s*\(\s*["'`]([^"'`]+)["'`]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(script)) !== null) {
    const localIdx = m.index + m[0].indexOf(m[2])
    out.push({ fn: m[1], source: m[2], index: fileOffset + localIdx })
  }
  return out
}

function findToastStrings(script: string, fileOffset: number): {
  source: string; index: number
}[] {
  const out: { source: string; index: number }[] = []
  const re = /\btext\s*:\s*["'`]([^"'`]+)["'`]/g
  let m: RegExpExecArray | null
  while ((m = re.exec(script)) !== null) {
    const v = m[1]
    if (!isTranslatable(v)) continue
    const localIdx = m.index + m[0].indexOf(v)
    out.push({ source: v, index: fileOffset + localIdx })
  }
  return out
}

function suggestKeyFor(kind: string, source: string, page: string): string {
  const slug = camelSlug(source)
  if (kind === 'text' || kind === 'text.interpolated') return `${page}.${slug}`
  if (kind === 'attr.placeholder') return `${page}.placeholder.${slug}`
  if (
    kind === 'attr.ariaLabel' ||
    kind === 'attr.title' ||
    kind === 'attr.alt' ||
    kind === 'attr.ariaDescription'
  ) {
    if (COMMON_GENERICS.has(slug)) return `common.${slug}`
    return `${page}.label.${slug}`
  }
  if (kind === 'confirm') return `${page}.confirm.${slug}`
  if (kind === 'alert' || kind === 'prompt') return `${page}.dialog.${slug}`
  if (kind === 'toast') return `${page}.toast.${slug}`
  return `${page}.${slug}`
}

export function extract(filePath: string): Manifest {
  const raw = readFileSync(filePath, 'utf-8')
  const cleaned = stripComments(stripStyleBlocks(raw))
  const en = loadEnJson()
  const page = pageNamespace(filePath)
  const candidates: Candidate[] = []

  const tpl = extractBlock(cleaned, 'template')
  const scr = extractBlock(cleaned, 'script')

  for (const t of findTagText(tpl.content, tpl.offset)) {
    const kind = t.interpolated ? 'text.interpolated' : 'text'
    candidates.push({
      kind,
      source: t.source,
      location: locationFromIndex(raw, t.index),
      suggestedKey: suggestKeyFor(kind, t.source, page),
      alreadyKey: findAlready(t.source, en),
    })
  }

  for (const a of findAttrValues(tpl.content, tpl.offset)) {
    const attrCamel = a.attr.replace(/-([a-z])/g, (_, c) => c.toUpperCase())
    const kind = `attr.${attrCamel}`
    candidates.push({
      kind,
      source: a.source,
      location: locationFromIndex(raw, a.index),
      suggestedKey: suggestKeyFor(kind, a.source, page),
      alreadyKey: findAlready(a.source, en),
    })
  }

  for (const d of findDialogCalls(scr.content, scr.offset)) {
    candidates.push({
      kind: d.fn,
      source: d.source,
      location: locationFromIndex(raw, d.index),
      suggestedKey: suggestKeyFor(d.fn, d.source, page),
      alreadyKey: findAlready(d.source, en),
    })
  }

  for (const t of findToastStrings(scr.content, scr.offset)) {
    candidates.push({
      kind: 'toast',
      source: t.source,
      location: locationFromIndex(raw, t.index),
      suggestedKey: suggestKeyFor('toast', t.source, page),
      alreadyKey: findAlready(t.source, en),
    })
  }

  return { file: filePath, candidates }
}

function isMainModule(): boolean {
  if (typeof (import.meta as { main?: boolean }).main === 'boolean') {
    return Boolean((import.meta as { main?: boolean }).main)
  }
  try {
    const metaPath = realpathSync(fileURLToPath(import.meta.url))
    const argv1 = process.argv[1] ? realpathSync(process.argv[1]) : ''
    return metaPath === argv1
  } catch {
    return false
  }
}

if (isMainModule()) {
  const arg = process.argv[2]
  if (!arg) {
    console.error('usage: npx tsx scripts/extract-i18n-candidates.ts <file.vue>')
    process.exit(1)
  }
  const manifest = extract(arg)
  console.log(JSON.stringify(manifest, null, 2))
}
