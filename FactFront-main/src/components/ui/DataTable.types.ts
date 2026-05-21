/**
 * Column definition for `<DataTable>`.
 * Generic over the row type so consumers get strong typing inside formatters and slots.
 *
 * Cell rendering precedence:
 *   1. `#cell-<key>` slot (when provided) — overrides everything below
 *   2. `column.format(value, row)` — when no slot
 *   3. `row[key]` raw, with '—' fallback for null/undefined/''
 */
export interface Column<Row = Record<string, unknown>> {
  /** Stable key. Used by sort emit, by `#cell-<key>` slot lookup, by `useColumnPreferences`.
   *  Must match `[a-zA-Z0-9_]+` so it can appear in slot names. */
  key: string;
  /** Header label (rendered in `<th>` and as the meta-stack label on auto mobile cards). */
  label: string;
  /** When true, renders a clickable `<th>` with arrow icons. Sort STATE stays with the parent. */
  sortable?: boolean;
  /** Cell horizontal alignment. Default 'left'. */
  align?: 'left' | 'right' | 'center';
  /** Tailwind width utility (e.g. 'w-28', 'min-w-[200px]'). Optional. */
  width?: string;
  /** Inline formatter. Used when no `#cell-<key>` slot is provided. */
  format?: (value: unknown, row: Row) => string;
  /** Hint for the auto-generated mobile card layout when no `#mobile-card` slot is provided. */
  mobile?: 'title' | 'subtitle' | 'meta' | 'hidden';
  /**
   * When 'left', the column sticks to the left edge during horizontal scroll.
   * v2: only 'left' is supported. At most one column may be sticky in v2;
   * if multiple are flagged, only the first wins (dev-time console warning).
   */
  sticky?: 'left';
}
