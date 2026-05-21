import api from '../plugin/axios';
import { Cache } from '../utils/cache';

export type InvoiceTemplateType = 'draft' | 'final';

export interface InvoiceTemplate {
  id?: string;
  name: string;
  expressDesignId?: string;
  bindings?: Record<string, any>;
  pageSettings?: Record<string, any>;
  studioProject?: any; // Données complètes du projet GrapesJS
  html?: string; // HTML du template (legacy top-level form, accepted on write)
  css?: string; // CSS du template (legacy top-level form, accepted on write)
  template?: { html?: string; css?: string }; // Nested form returned by backend on read
  status?: string;
  /** 'draft' | 'final' — set at creation only, immutable thereafter. Defaults to 'final'. */
  type?: InvoiceTemplateType;
  thumbnailUrl?: string;
  lastModified?: string;
  version?: string;
}

// Cache instances for invoice templates
// Templates rarely change, so use longer TTL (30 minutes)
const templateListCache = new Cache<InvoiceTemplate[]>({ ttl: 30 * 60 * 1000, maxSize: 1 });
const templateCache = new Cache<InvoiceTemplate>({ ttl: 30 * 60 * 1000, maxSize: 50 });

export async function listTemplates(): Promise<InvoiceTemplate[]> {
  // Try to get from cache first
  const cached = templateListCache.get('all');
  if (cached) {
    return cached;
  }

  // Fetch from API and cache the result
  try {
    const res = await api.get('/invoice-templates');
    const templates = res.data;
    templateListCache.set('all', templates);

    // NOTE: do NOT cache list items in `templateCache`. The list endpoint
    // returns summaries (no template.html/css), so caching them under the
    // template-detail key would poison subsequent getTemplate() reads and
    // open the editor on an empty document.

    return templates;
  } catch (error) {
    console.error('[InvoiceTemplateService] Failed to list templates:', error);
    throw error;
  }
}

export async function getTemplate(id: string): Promise<InvoiceTemplate> {
  // Try to get from cache first
  const cached = templateCache.get(id);
  if (cached) {
    return cached;
  }

  // Fetch from API and cache the result
  try {
    const res = await api.get(`/invoice-templates/${encodeURIComponent(id)}`);
    const template = res.data;
    templateCache.set(id, template);

    return template;
  } catch (error) {
    console.error(`[InvoiceTemplateService] Failed to get template ${id}:`, error);
    throw error;
  }
}

export async function createTemplate(payload: InvoiceTemplate): Promise<InvoiceTemplate> {
  try {
    const res = await api.post('/invoice-templates', payload);

    // Invalidate list cache when a new template is created
    templateListCache.clear();

    return res.data;
  } catch (error) {
    console.error('[InvoiceTemplateService] Failed to create template:', error);
    throw error;
  }
}

export async function updateTemplate(id: string, payload: InvoiceTemplate): Promise<InvoiceTemplate> {
  try {
    const res = await api.put(`/invoice-templates/${encodeURIComponent(id)}`, payload);

    // Invalidate caches when a template is updated
    templateCache.delete(id);
    templateListCache.clear();

    return res.data;
  } catch (error) {
    console.error(`[InvoiceTemplateService] Failed to update template ${id}:`, error);
    throw error;
  }
}

/**
 * Atomically activates a template for its type, archiving any other previously-active
 * template of the same type. Backend enforces the "at most one active per type" invariant.
 */
export async function activateTemplate(id: string): Promise<InvoiceTemplate> {
  try {
    const res = await api.post(`/invoice-templates/${encodeURIComponent(id)}/activate`);
    templateCache.delete(id);
    templateListCache.clear();
    return res.data;
  } catch (error) {
    console.error(`[InvoiceTemplateService] Failed to activate template ${id}:`, error);
    throw error;
  }
}

export async function previewTemplate(id: string, body: any): Promise<Blob> {
  try {
    const res = await api.post(`/invoice-templates/${encodeURIComponent(id)}/preview`, body, {
      responseType: 'blob',
    });
    return res.data;
  } catch (error) {
    console.error(`[InvoiceTemplateService] Failed to preview template ${id}:`, error);
    throw error;
  }
}

// Cache management utilities
export const cacheUtils = {
  /**
   * Clear all template caches
   */
  clearAll: () => {
    templateListCache.clear();
    templateCache.clear();
  },

  /**
   * Clear template list cache only
   */
  clearList: () => {
    templateListCache.clear();
  },

  /**
   * Clear specific template from cache
   */
  clearTemplate: (id: string) => {
    templateCache.delete(id);
  },

  /**
   * Get cache statistics
   */
  getStats: () => ({
    list: templateListCache.getStats(),
    templates: templateCache.getStats(),
  }),

  /**
   * Clean up expired entries
   */
  cleanup: () => {
    const listDeleted = templateListCache.cleanup();
    const templatesDeleted = templateCache.cleanup();
    return { listDeleted, templatesDeleted };
  },
};

export default {
  listTemplates,
  getTemplate,
  createTemplate,
  updateTemplate,
  activateTemplate,
  previewTemplate,
  cacheUtils,
};