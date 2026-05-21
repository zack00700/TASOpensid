// frontend/utils/normalize.ts

const ID_KEYS = ['id', '_id', '$oid', '$id', 'oid'] as const;

function isPrimitiveId(value: unknown): value is string | number | bigint {
  return (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'bigint'
  );
}

export function extractStringId(source: any): string | undefined {
  if (source == null) {
    return undefined;
  }

  const queue: any[] = [source];
  const visited = new Set<any>();

  while (queue.length > 0) {
    const current = queue.shift();

    if (current == null) {
      continue;
    }

    if (isPrimitiveId(current)) {
      return String(current);
    }

    if (typeof current === 'object') {
      if (visited.has(current)) {
        continue;
      }
      visited.add(current);

      for (const key of ID_KEYS) {
        if (Object.prototype.hasOwnProperty.call(current, key)) {
          queue.push((current as Record<string, any>)[key]);
        }
      }
    }
  }

  return undefined;
}

export function normalizeItem<T extends Record<string, any>>(doc: T): T {
  if (!doc || typeof doc !== 'object') {
    return doc;
  }

  const normalizedId = extractStringId(doc);
  const copy = { ...doc } as Record<string, any>;

  if (normalizedId !== undefined) {
    copy.id = normalizedId;
  }

  return copy as T;
}
