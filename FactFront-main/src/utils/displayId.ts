/**
 * Short, opaque display form for an internal UUID.
 *
 * Cahier de recette TC-15 asked operators to stop seeing raw `0a5f6b91-3e2c-…`
 * identifiers on item/visit/BL pages. We don't actually need to encrypt — we
 * just need a shorter, deterministic representation that is still copy/paste-able
 * and recognisable across reloads. The full UUID is preserved as a tooltip for
 * users who genuinely need it (and as the canonical value in URLs/queries).
 *
 * The chosen format is `#XXXXXXXX` where the 8 chars are the last segment of
 * the UUID upper-cased (sufficient to distinguish all items inside a single
 * tenant). For non-UUID values we fall back to truncation.
 */
export function formatDisplayId(raw: string | null | undefined): string {
  if (raw === null || raw === undefined) return '—';
  const s = String(raw).trim();
  if (!s) return '—';

  // UUIDv4 with hyphens — keep the last group, which is the 12-char node section.
  const uuid = s.match(/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-([0-9a-fA-F]{12})$/);
  if (uuid) {
    return `#${uuid[1].slice(-8).toUpperCase()}`;
  }

  // Bare 32-char UUID (no hyphens) — same idea.
  if (/^[0-9a-fA-F]{32}$/.test(s)) {
    return `#${s.slice(-8).toUpperCase()}`;
  }

  // Anything else: keep short, just enough to recognise.
  return s.length > 12 ? `${s.slice(0, 4)}…${s.slice(-4)}` : s;
}
