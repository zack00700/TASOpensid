import { Item, Lifecycle, Event } from '../types/item';
import { EventHandler } from './eventHandler';
import api from '../plugin/axios';
import { normalizeItem } from '../utils/normalize';
import { Cache } from '../utils/cache';

export class ItemService {
  private static instance: ItemService;
  private eventHandler: EventHandler;

  private constructor() {
    this.eventHandler = EventHandler.getInstance();
  }

  public static getInstance(): ItemService {
    if (!ItemService.instance) {
      ItemService.instance = new ItemService();
    }
    return ItemService.instance;
  }

  public getActiveLifecycle(item: Item): Lifecycle | undefined {
    return item.lifeCycles.find(lifecycle => lifecycle.status === 'In Progress');
  }

  public getCompletedLifecycles(item: Item): Lifecycle[] {
    return item.lifeCycles.filter(lifecycle => lifecycle.status === 'Completed');
  }

  public getLifecyclesInDateRange(
    item: Item,
    startDate: Date,
    endDate: Date
  ): Lifecycle[] {
    return item.lifeCycles.filter(lifecycle => {
      const lifecycleStart = new Date(lifecycle.startTime);
      const lifecycleEnd = lifecycle.endTime ? new Date(lifecycle.endTime) : new Date();
      return lifecycleStart >= startDate && lifecycleEnd <= endDate;
    });
  }

  public async addEvent(item: Item, event: Event): Promise<string> {
    try {
      console.debug('addEvent called', { event, item });
      return await this.eventHandler.processEvent(event, item);
    } catch (error) {
      console.error('[ItemService] Failed to process event:', error);
      throw error;
    }
  }

  public cancelCurrentLifecycle(item: Item, reason: string): Item {
    if (!item.currentLifecycleId) {
      throw new Error('No active lifecycle to cancel');
    }

    const cancelEvent: Event = {
      id: "1",
      timestamp: new Date().toISOString(),
      eventType: 'OUT',
      itemId: item.id,
      lifecycleId: item.currentLifecycleId,
      notes: reason
    };

    const updatedLifecycles = item.lifeCycles.map(lifecycle => {
      if (lifecycle.id === item.currentLifecycleId) {
        return {
          ...lifecycle,
          endTime: cancelEvent.timestamp,
          status: 'Cancelled',
          events: [...lifecycle.events, cancelEvent]
        };
      }
      return lifecycle;
    });

    return {
      ...item,
      currentLifecycleId: undefined,
      lifeCycles: updatedLifecycles
    };
  }

  public getLifecycleDuration(lifecycle: Lifecycle): number {
    const start = new Date(lifecycle.startTime);
    const end = lifecycle.endTime ? new Date(lifecycle.endTime) : new Date();
    return end.getTime() - start.getTime();
  }

  public getAverageLifecycleDuration(item: Item): number {
    const completedLifecycles = this.getCompletedLifecycles(item);
    if (completedLifecycles.length === 0) return 0;

    const totalDuration = completedLifecycles.reduce(
      (sum, lifecycle) => sum + this.getLifecycleDuration(lifecycle),
      0
    );

    return totalDuration / completedLifecycles.length;
  }
}

// Cache instances for item data
// Items cache: 60 second TTL, max 200 items (to handle large bill lists)
const itemCache = new Cache<Item>({ ttl: 60 * 1000, maxSize: 200 });

// Lifecycles cache: 60 second TTL, max 100 items
const lifecyclesCache = new Cache<any>({ ttl: 60 * 1000, maxSize: 100 });

// ✅ FIXED: Updated interface to include optional id and other fields
interface ItemCreatePayload {
  id?: string; // Now accepts UUID
  type: string;
  itemNumber: string;
  status: string;
  billOfLadingId?: string;
  ownerId?: string;
  position?: string;
  weight?: number;
  volume?: number;
  lastInspectionDate?: string;
  nextInspectionDate?: string;
  notes?: string;
  itemStatus?: string;
  itemType?: string;
}

export const create = async (data: ItemCreatePayload): Promise<any> => {
  try {
    const response = await api.post('/items', data);

    // Invalidate cache when a new item is created
    // We don't clear the entire cache, but if the item has an ID, we ensure it's not cached
    if (response.data?.id) {
      itemCache.delete(response.data.id);
    }

    return response.data;
  } catch (error) {
    console.error('[ItemService] Failed to create item:', error);
    throw error;
  }
};

export const get = async (id: string): Promise<Item> => {
  // Try to get from cache first
  const cached = itemCache.get(id);
  if (cached) {
    return cached;
  }

  try {
    // Fetch from API and cache the result
    const response = await api.get(`/items/${id}`);
    const item = normalizeItem(response.data) as Item;
    itemCache.set(id, item);

    return item;
  } catch (error) {
    console.error(`[ItemService] Failed to fetch item ${id}:`, error);
    throw error;
  }
};

// ✅ FIXED: Added fetchMany method to handle multiple items with caching
export const fetchMany = async (ids: string[]): Promise<any[]> => {
  if (!ids || ids.length === 0) return [];

  const results: any[] = [];
  const idsToFetch: string[] = [];

  // Check cache first
  for (const id of ids) {
    const cached = itemCache.get(id);
    if (cached) {
      results.push(cached);
    } else {
      idsToFetch.push(id);
    }
  }

  // Fetch uncached items concurrently
  if (idsToFetch.length > 0) {
    const fetchedItems = await Promise.all(
      idsToFetch.map(async (id) => {
        try {
          const response = await api.get(`/items/${id}`);
          const item = response.data;
          // Cache the fetched item
          itemCache.set(id, item);
          return item;
        } catch (error) {
          console.warn(`Failed to fetch item with id ${id}:`, error);
          return null;
        }
      })
    );

    // Add fetched items to results
    results.push(...fetchedItems.filter(item => item !== null));
  }

  return results;
};

export interface AddEventPayload {
  eventId: string;
  eventDate: string;
}

export const addEventToItem = async (
  itemId: string,
  payload: AddEventPayload,
): Promise<any> => {
  try {
    const response = await api.post(`/items/${itemId}/event`, payload);

    // Invalidate cache for this item and its lifecycles when an event is added
    itemCache.delete(itemId);
    lifecyclesCache.invalidatePattern(new RegExp(`^${itemId}_`));

    return response.data;
  } catch (error) {
    console.error(`[ItemService] Failed to add event to item ${itemId}:`, error);
    throw error;
  }
};

export const getLifecycles = async (
  itemId: string,
  expandEvents = true,
): Promise<any> => {
  // Create cache key based on itemId and expandEvents flag
  const cacheKey = `${itemId}_${expandEvents}`;

  // Try to get from cache first
  const cached = lifecyclesCache.get(cacheKey);
  if (cached) {
    return cached;
  }

  try {
    // Fetch from API and cache the result
    const response = await api.get(`/items/${itemId}/lifecycles`, {
      params: expandEvents ? { expandEvents: true } : {},
    });
    const lifecycles = response.data;
    lifecyclesCache.set(cacheKey, lifecycles);

    return lifecycles;
  } catch (error) {
    console.error(`[ItemService] Failed to fetch lifecycles for item ${itemId}:`, error);
    throw error;
  }
};

// Cache management utilities
export const cacheUtils = {
  /**
   * Clear all item caches
   */
  clearAll: () => {
    itemCache.clear();
    lifecyclesCache.clear();
  },

  /**
   * Clear item cache only
   */
  clearItems: () => {
    itemCache.clear();
  },

  /**
   * Clear lifecycles cache only
   */
  clearLifecycles: () => {
    lifecyclesCache.clear();
  },

  /**
   * Get cache statistics
   */
  getStats: () => ({
    items: itemCache.getStats(),
    lifecycles: lifecyclesCache.getStats(),
  }),

  /**
   * Clean up expired entries
   */
  cleanup: () => {
    const itemsDeleted = itemCache.cleanup();
    const lifecyclesDeleted = lifecyclesCache.cleanup();
    return { itemsDeleted, lifecyclesDeleted };
  },
};

export default {
  create,
  get,
  fetchMany,
  addEventToItem,
  getLifecycles,
  cacheUtils,
};
