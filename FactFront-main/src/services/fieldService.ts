import api from '../plugin/axios';
import type { Field } from '../types/field';
import { Cache } from '../utils/cache';

export interface LanguagePayload {
  code: string;
}

export interface TranslationPayload {
  value: string;
}

// Cache for field data (static metadata, long TTL)
// Fields rarely change, so cache for 1 hour
const fieldsCache = new Cache<Field[]>({ ttl: 60 * 60 * 1000, maxSize: 1 });

export const getFields = async (): Promise<Field[]> => {
  // Try to get from cache first
  const cached = fieldsCache.get('all');
  if (cached) {
    return cached;
  }

  // Fetch from API and cache the result
  try {
    const response = await api.get('/fields');
    const fields = response.data;
    fieldsCache.set('all', fields);

    return fields;
  } catch (error) {
    console.error('[FieldService] Failed to fetch fields:', error);
    throw error;
  }
};

export const addLanguage = async (payload: LanguagePayload): Promise<void> => {
  try {
    await api.post('/languages', payload);

    // Invalidate fields cache when a language is added
    fieldsCache.clear();
  } catch (error) {
    console.error('[FieldService] Failed to add language:', error);
    throw error;
  }
};

export const updateTranslation = async (
  fieldKey: string,
  lang: string,
  value: string,
): Promise<void> => {
  try {
    await api.put(`/fields/${fieldKey}/translations/${lang}`, { value });

    // Invalidate fields cache when a translation is updated
    fieldsCache.clear();
  } catch (error) {
    console.error(`[FieldService] Failed to update translation for field ${fieldKey} / lang ${lang}:`, error);
    throw error;
  }
};

// Cache management utilities
export const cacheUtils = {
  /**
   * Clear fields cache
   */
  clear: () => {
    fieldsCache.clear();
  },

  /**
   * Get cache statistics
   */
  getStats: () => fieldsCache.getStats(),

  /**
   * Clean up expired entries
   */
  cleanup: () => fieldsCache.cleanup(),
};

export default {
  getFields,
  addLanguage,
  updateTranslation,
  cacheUtils,
};
