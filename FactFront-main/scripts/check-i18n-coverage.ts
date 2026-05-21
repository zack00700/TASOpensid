#!/usr/bin/env bun
/**
 * Check that every t('...') / $t('...') key referenced in src/ has an
 * entry in src/locales/en.json. Exits non-zero on missing keys.
 *
 * Usage:  bun run scripts/check-i18n-coverage.ts
 */
import { readdirSync, readFileSync, existsSync, statSync, realpathSync } from 'node:fs'
import { resolve, join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const T_CALL = /(?<![a-zA-Z_$])\$?t\s*\(\s*['"`]([a-zA-Z][\w.-]*)['"`]/g

function walk(dir: string, out: string[] = []): string[] {
  if (!existsSync(dir)) return out
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    const st = statSync(path)
    if (st.isDirectory()) walk(path, out)
    else if (st.isFile()) {
      const ext = extname(path)
      if (ext === '.vue' || ext === '.ts' || ext === '.js') out.push(path)
    }
  }
  return out
}

export function findUsedKeys(rootDir: string): Set<string> {
  const used = new Set<string>()
  const srcDir = resolve(rootDir, 'src')
  for (const file of walk(srcDir)) {
    const content = readFileSync(file, 'utf-8')
    let m: RegExpExecArray | null
    T_CALL.lastIndex = 0
    while ((m = T_CALL.exec(content)) !== null) {
      used.add(m[1])
    }
  }
  return used
}

export function missingKeys(rootDir: string): string[] {
  const used = findUsedKeys(rootDir)
  const enPath = resolve(rootDir, 'src/locales/en.json')
  const en: Record<string, string> = existsSync(enPath)
    ? JSON.parse(readFileSync(enPath, 'utf-8'))
    : {}
  const missing: string[] = []
  for (const key of used) if (!(key in en)) missing.push(key)
  return missing.sort()
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
  const root = process.cwd()
  const missing = missingKeys(root)
  if (missing.length === 0) {
    console.log(`✓ i18n coverage: ${findUsedKeys(root).size} keys used, all present in en.json`)
    process.exit(0)
  }
  console.error(`✗ i18n coverage: ${missing.length} missing key(s) in src/locales/en.json:`)
  for (const k of missing) console.error(`  - ${k}`)
  process.exit(1)
}
