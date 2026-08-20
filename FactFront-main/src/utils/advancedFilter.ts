/**
 * Client-side evaluation of the conditions emitted by `AdvancedFilter.vue`.
 *
 * The component emits `FilterGroup[]` — `{ id, conditions[], logicalOperator }` —
 * not a flat list, and each condition carries an operator. Screens used to read
 * `.field` straight off the groups, which is always `undefined`, so applying a
 * filter silently changed nothing.
 *
 * Pages backed by a paginated API (Bills of Lading, Items) push the flattened
 * conditions to the server instead; this module is for the screens that hold the
 * whole collection in memory and filter it with a computed.
 */

export interface AdvancedFilterCondition {
  id?: string;
  field: string;
  operator: string;
  value: unknown;
  /** Upper bound, used by the `between` operator. */
  value2?: unknown;
  logicalOperator?: 'AND' | 'OR';
}

export interface AdvancedFilterGroup {
  id?: string;
  conditions?: AdvancedFilterCondition[];
  logicalOperator?: 'AND' | 'OR';
}

/** Pulls every condition out of the emitted groups, dropping empty ones. */
export function flattenConditions(groups: AdvancedFilterGroup[] | null | undefined): AdvancedFilterCondition[] {
  return (groups ?? [])
    .flatMap((group) => group?.conditions ?? [])
    .filter((condition) => !!condition?.field && !isBlank(condition.value));
}

function isBlank(value: unknown): boolean {
  return value === '' || value === null || value === undefined;
}

function asText(value: unknown): string {
  return value == null ? '' : String(value).toLowerCase();
}

function asNumber(value: unknown): number {
  const n = typeof value === 'number' ? value : parseFloat(String(value));
  return Number.isFinite(n) ? n : NaN;
}

function asTime(value: unknown): number {
  if (value == null) return NaN;
  return new Date(value as string).getTime();
}

/**
 * Reads `row.field`, following dots so nested fields such as
 * `calculationMode.eventConfig.eventName` work.
 */
function readField(row: Record<string, any>, field: string): unknown {
  return field
    .split('.')
    .reduce<any>((acc, key) => (acc == null ? undefined : acc[key]), row);
}

/** Evaluates one condition against a row. Unknown operators fall back to equality. */
export function matchesCondition(row: Record<string, any>, condition: AdvancedFilterCondition): boolean {
  const actual = readField(row, condition.field);

  switch (condition.operator) {
    case 'contains':
      return asText(actual).includes(asText(condition.value));
    case 'not_contains':
      return !asText(actual).includes(asText(condition.value));
    case 'starts_with':
      return asText(actual).startsWith(asText(condition.value));
    case 'ends_with':
      return asText(actual).endsWith(asText(condition.value));
    case 'not_equals':
      return asText(actual) !== asText(condition.value);
    case 'greater_than':
      return asNumber(actual) > asNumber(condition.value);
    case 'less_than':
      return asNumber(actual) < asNumber(condition.value);
    case 'before':
      return asTime(actual) < asTime(condition.value);
    case 'after':
      return asTime(actual) > asTime(condition.value);
    case 'between': {
      // Dates and numbers share this operator; compare as dates when the raw
      // value doesn't parse as a number.
      const lowRaw = condition.value;
      const highRaw = condition.value2;
      if (isBlank(highRaw)) return true;
      const numeric = !Number.isNaN(asNumber(actual)) && !Number.isNaN(asNumber(lowRaw));
      const current = numeric ? asNumber(actual) : asTime(actual);
      const low = numeric ? asNumber(lowRaw) : asTime(lowRaw);
      const high = numeric ? asNumber(highRaw) : asTime(highRaw);
      if (Number.isNaN(current) || Number.isNaN(low) || Number.isNaN(high)) return false;
      return current >= low && current <= high;
    }
    case 'equals':
    default:
      return asText(actual) === asText(condition.value);
  }
}

/**
 * Keeps the rows satisfying every condition (AND). Per-condition
 * `logicalOperator` is not honoured yet — `AdvancedFilter` exposes the control
 * but no screen has needed OR semantics, and guessing at precedence would be
 * worse than the predictable conjunction.
 */
export function applyConditions<T extends Record<string, any>>(
  rows: T[] | null | undefined,
  conditions: AdvancedFilterCondition[],
): T[] {
  if (!rows?.length || !conditions.length) return rows ?? [];
  return rows.filter((row) => conditions.every((condition) => matchesCondition(row, condition)));
}
