/**
 * Cache utility with TTL (Time To Live) support and LRU eviction
 */

export interface CacheOptions {
  /**
   * Time to live in milliseconds. Default: 5 minutes
   */
  ttl?: number;
  /**
   * Maximum number of entries. When exceeded, least recently used entries are evicted.
   * Default: 100
   */
  maxSize?: number;
}

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
  lastAccessed: number;
}

export class Cache<T = any> {
  private cache = new Map<string, CacheEntry<T>>();
  private readonly ttl: number;
  private readonly maxSize: number;

  constructor(options: CacheOptions = {}) {
    this.ttl = options.ttl ?? 5 * 60 * 1000; // Default 5 minutes
    this.maxSize = options.maxSize ?? 100;
  }

  /**
   * Get value from cache
   * @param key Cache key
   * @returns Cached value or undefined if not found or expired
   */
  get(key: string): T | undefined {
    const entry = this.cache.get(key);

    if (!entry) {
      return undefined;
    }

    // Check if expired
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return undefined;
    }

    // Update last accessed time for LRU
    entry.lastAccessed = Date.now();
    return entry.value;
  }

  /**
   * Set value in cache
   * @param key Cache key
   * @param value Value to cache
   * @param customTtl Optional custom TTL for this entry (in milliseconds)
   */
  set(key: string, value: T, customTtl?: number): void {
    const ttl = customTtl ?? this.ttl;
    const now = Date.now();

    this.cache.set(key, {
      value,
      expiresAt: now + ttl,
      lastAccessed: now,
    });

    // Enforce max size with LRU eviction
    if (this.cache.size > this.maxSize) {
      this.evictLRU();
    }
  }

  /**
   * Check if key exists and is not expired
   * @param key Cache key
   */
  has(key: string): boolean {
    return this.get(key) !== undefined;
  }

  /**
   * Delete specific key from cache
   * @param key Cache key
   */
  delete(key: string): boolean {
    return this.cache.delete(key);
  }

  /**
   * Clear all entries from cache
   */
  clear(): void {
    this.cache.clear();
  }

  /**
   * Delete all keys matching a pattern
   * @param pattern String pattern or RegExp to match keys
   */
  invalidatePattern(pattern: string | RegExp): number {
    const regex = typeof pattern === 'string' ? new RegExp(pattern) : pattern;
    let deletedCount = 0;

    for (const key of this.cache.keys()) {
      if (regex.test(key)) {
        this.cache.delete(key);
        deletedCount++;
      }
    }

    return deletedCount;
  }

  /**
   * Get cache statistics
   */
  getStats() {
    const now = Date.now();
    let validEntries = 0;
    let expiredEntries = 0;

    for (const entry of this.cache.values()) {
      if (now > entry.expiresAt) {
        expiredEntries++;
      } else {
        validEntries++;
      }
    }

    return {
      size: this.cache.size,
      validEntries,
      expiredEntries,
      maxSize: this.maxSize,
      ttl: this.ttl,
    };
  }

  /**
   * Remove expired entries from cache
   */
  cleanup(): number {
    const now = Date.now();
    let deletedCount = 0;

    for (const [key, entry] of this.cache.entries()) {
      if (now > entry.expiresAt) {
        this.cache.delete(key);
        deletedCount++;
      }
    }

    return deletedCount;
  }

  /**
   * Evict least recently used entry
   */
  private evictLRU(): void {
    let lruKey: string | undefined;
    let lruTime = Infinity;

    for (const [key, entry] of this.cache.entries()) {
      if (entry.lastAccessed < lruTime) {
        lruTime = entry.lastAccessed;
        lruKey = key;
      }
    }

    if (lruKey) {
      this.cache.delete(lruKey);
    }
  }

  /**
   * Get or set value using a factory function
   * @param key Cache key
   * @param factory Function to generate value if not in cache
   * @param customTtl Optional custom TTL for this entry
   */
  async getOrSet(
    key: string,
    factory: () => Promise<T>,
    customTtl?: number
  ): Promise<T> {
    const cached = this.get(key);
    if (cached !== undefined) {
      return cached;
    }

    const value = await factory();
    this.set(key, value, customTtl);
    return value;
  }
}

/**
 * Create a cached version of an async function
 * @param fn Function to cache
 * @param options Cache options
 * @param keyGenerator Function to generate cache key from arguments
 */
export function createCachedFunction<TArgs extends any[], TResult>(
  fn: (...args: TArgs) => Promise<TResult>,
  options: CacheOptions = {},
  keyGenerator: (...args: TArgs) => string = (...args) => JSON.stringify(args)
) {
  const cache = new Cache<TResult>(options);

  const cachedFn = async (...args: TArgs): Promise<TResult> => {
    const key = keyGenerator(...args);
    return cache.getOrSet(key, () => fn(...args));
  };

  // Expose cache for manual operations
  cachedFn.cache = cache;
  cachedFn.clearCache = () => cache.clear();
  cachedFn.invalidate = (pattern: string | RegExp) => cache.invalidatePattern(pattern);

  return cachedFn;
}
