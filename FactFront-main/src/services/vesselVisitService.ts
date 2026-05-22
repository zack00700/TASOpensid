// src/services/vesselVisitService.ts

import api from '../plugin/axios';
import { Cache } from '../utils/cache';

/**
 * Types
 */
export interface VesselVisit {
  id: string;
  vesselName: string;
  imo?: string | null;
  callSign?: string | null;
  voyageIn?: string | null;
  voyageOut?: string | null;
  operator?: string | null;
  port?: string | null;
  terminal?: string | null;
  berth?: string | null;
  eta?: string | null; // ISO
  etd?: string | null; // ISO
  ata?: string | null; // ISO
  atd?: string | null; // ISO
}

/**
 * Small helper to clean user input so your backend never receives
 * things like "Neptune (N/A" or dangling "(" which were causing 500s.
 *
 * Rules:
 * - cut everything from the first "("
 * - remove any parentheses just in case
 * - remove literal "N/A"
 * - collapse whitespace
 */
export function sanitizeVesselQuery(raw: string): string {
  return String(raw || "")
    .replace(/\(.*/g, "")     // cut from first "("
    .replace(/[()]/g, "")     // strip any remaining parentheses
    .replace(/\bN\/A\b/gi, "")// drop N/A tokens
    .replace(/\s+/g, " ")
    .trim();
}

// Relative path — the shared axios instance handles the base URL and auth headers.
const BASE = "/vessel-visits";

// Cache instances
// Search cache: 5 minute TTL (search queries are often repeated during data entry)
const searchCache = new Cache<VesselVisit[]>({ ttl: 5 * 60 * 1000, maxSize: 50 });

// Vessel visit cache: 10 minute TTL for individual visits
const vesselVisitCache = new Cache<VesselVisit>({ ttl: 10 * 60 * 1000, maxSize: 100 });

/**
 * Search vessel visits by a free-text query.
 * - Returns [] for very short queries (length < 2)
 * - Throws if the response is not OK
 */
async function search(raw: string): Promise<VesselVisit[]> {
  const q = sanitizeVesselQuery(raw);
  if (q.length < 2) return [];

  // Check cache first
  const cached = searchCache.get(q);
  if (cached) {
    return cached;
  }

  try {
    // Use the shared axios instance so the Bearer token and base URL apply.
    // Previously this called raw fetch('/api/vessel-visits/search'), which both
    // bypassed auth and missed the proxy rewrite — every search came back 401/404.
    const res = await api.get(`${BASE}/search`, { params: { q } });
    const data = res.data as VesselVisit[] | { data: VesselVisit[] };
    const results: VesselVisit[] = Array.isArray(data)
      ? data
      : ((data as { data?: VesselVisit[] }).data ?? []);

    // Cache the search results
    searchCache.set(q, results);

    // Also cache individual vessel visits from the search results
    results.forEach((visit: VesselVisit) => {
      if (visit.id) {
        vesselVisitCache.set(visit.id, visit);
      }
    });

    return results;
  } catch (error) {
    console.error(`[VesselVisitService] Failed to search vessel visits for query "${q}":`, error);
    throw error;
  }
}

/**
 * Optional: fetch a single visit by id
 */
async function getById(id: string): Promise<VesselVisit | null> {
  if (!id) return null;

  // Check cache first
  const cached = vesselVisitCache.get(id);
  if (cached) {
    return cached;
  }

  try {
    const res = await api.get(`${BASE}/${encodeURIComponent(id)}`);
    const visit = res.data as VesselVisit | null;

    if (visit) {
      vesselVisitCache.set(id, visit);
    }

    return visit;
  } catch (error: any) {
    if (error?.response?.status === 404) return null;
    console.error(`[VesselVisitService] Failed to fetch vessel visit ${id}:`, error);
    throw error;
  }
}

/**
 * Convenience: minimal snapshot mapper (if you need it here).
 * If you already have this in billOfLadingService, you can remove this export.
 */
export function toSnapshot(v: VesselVisit) {
  return {
    id: v.id,
    vesselName: v.vesselName || "",
    imo: v.imo || "",
    callSign: v.callSign || "",
    voyageIn: v.voyageIn || "",
    voyageOut: v.voyageOut || "",
    operator: v.operator || "",
    port: v.port || "",
    terminal: v.terminal || "",
    berth: v.berth || "",
    eta: v.eta || null,
    etd: v.etd || null,
    ata: v.ata || null,
    atd: v.atd || null,
  };
}

// Cache management utilities
const cacheUtils = {
  /**
   * Clear all vessel visit caches
   */
  clearAll: () => {
    searchCache.clear();
    vesselVisitCache.clear();
  },

  /**
   * Clear search cache only
   */
  clearSearch: () => {
    searchCache.clear();
  },

  /**
   * Clear vessel visit cache only
   */
  clearVessels: () => {
    vesselVisitCache.clear();
  },

  /**
   * Get cache statistics
   */
  getStats: () => ({
    search: searchCache.getStats(),
    vessels: vesselVisitCache.getStats(),
  }),

  /**
   * Clean up expired entries
   */
  cleanup: () => {
    const searchDeleted = searchCache.cleanup();
    const vesselsDeleted = vesselVisitCache.cleanup();
    return { searchDeleted, vesselsDeleted };
  },
};

const vesselVisitService = {
  search,
  getById,
  sanitizeVesselQuery,
  toSnapshot,
  cacheUtils,
};

export default vesselVisitService;
