import api from '../plugin/axios';
import itemService from './itemService';
import { normalizeItem } from '../utils/normalize';

export interface Commodity {
  description: string;
  weightKg: number;
  volumeM3: number;
  packagesNumber: number;
  hazardous: boolean;
  hazardClass?: string;
  unNumber?: string;
}

export interface Item {
  id?: string;
  clientId?: string;
  type: 'container' | 'breakbulk' | 'vehicle';
  itemNumber: string;
  status: string;
}

export interface BillOfLading {
  id?: string;
  blNumber: string;
  status: 'Draft' | 'Final' | 'Cancelled';
  shipper: string;
  consignee: string;
  notifyParty: string;
  transportType: 'Vessel' | 'Train' | 'Truck';
  vessel: string;
  voyage: string;
  portOfLoading: string;
  portOfDischarge: string;
  placeOfDelivery: string;
  driver: string;
  trainNumber: string;
  truckNumber: string;
  commodity: Commodity;
  items: Item[];
  transportSnapshot?: any;
  createdAt: string;
  updatedAt: string;
}

export interface VesselSnapshot {
  id?: string;
  vesselName?: string;
  imo?: string;
  callSign?: string;
  voyageIn?: string;
  voyageOut?: string;
  operator?: string;
  port?: string;
  terminal?: string;
  berth?: string;
  eta?: string;
  etd?: string;
  ata?: string | null;
  atd?: string | null;
  capturedAt?: string;
}

export interface TransportUpdateRequest {
  type: 'VESSEL' | 'TRAIN' | 'TRUCK';
  vesselVisitId?: string;
}

// Pagination interfaces
export interface PaginationParams {
  page: number;
  size: number;
  search?: string;
  status?: string;
  shipper?: string;
  vessel?: string;
  transportType?: string;
}

export interface PaginationMetadata {
  currentPage: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PagedResponse<T> {
  items: T[];
  pagination: PaginationMetadata;
}

export const updateTransport = async (
  bolId: string,
  data: TransportUpdateRequest,
): Promise<VesselSnapshot> => {
  try {
    const response = await api.put(`/billoflading/${bolId}/transport`, data);
    return response.data;
  } catch (error) {
    console.error(`[BillOfLadingService] Failed to update transport for BOL ${bolId}:`, error);
    throw error;
  }
};

export const refreshTransport = async (bolId: string): Promise<VesselSnapshot> => {
  try {
    const response = await api.put(`/billoflading/${bolId}/transport/refresh`);
    return response.data;
  } catch (error) {
    console.error(`[BillOfLadingService] Failed to refresh transport for BOL ${bolId}:`, error);
    throw error;
  }
};

export const mapVisitToSnapshot = (visit: any): VesselSnapshot => ({
  id: visit.id,
  vesselName: visit.vesselName,
  imo: visit.imo,
  callSign: visit.callSign,
  voyageIn: visit.voyageIn,
  voyageOut: visit.voyageOut,
  operator: visit.operator,
  port: visit.port,
  terminal: visit.terminal,
  berth: visit.berth,
  eta: visit.eta,
  etd: visit.etd,
  ata: visit.ata,
  atd: visit.atd,
  capturedAt: new Date().toISOString(),
});

// Helper function to enrich bills with their items
const enrichBillsWithItems = async (bills: any[]): Promise<BillOfLading[]> => {
  return await Promise.all(
    bills.map(async (bill: any) => {
      const itemIds: string[] = bill.itemIds || [];
      const fetchedItems = await Promise.all(
        itemIds.map((id: string) => itemService.get(id))
      );
      const items = fetchedItems.map(item => normalizeItem(item) as Item);
      return { ...bill, items } as BillOfLading;
    })
  );
};

// Legacy method for backward compatibility
export const list = async (): Promise<BillOfLading[]> => {
  try {
    const response = await api.get('/billoflading');
    return await enrichBillsWithItems(response.data as any[]);
  } catch (error) {
    console.error('[BillOfLadingService] Failed to list bills of lading:', error);
    throw error;
  }
};

// New paginated fetch method
export const fetchPaginated = async (params: PaginationParams): Promise<PagedResponse<BillOfLading>> => {
  try {
    const queryParams = new URLSearchParams();

    queryParams.append('page', params.page.toString());
    queryParams.append('size', params.size.toString());

    if (params.search && params.search.trim()) {
      queryParams.append('search', params.search.trim());
    }
    if (params.status && params.status.trim()) {
      queryParams.append('status', params.status.trim());
    }
    if (params.shipper && params.shipper.trim()) {
      queryParams.append('shipper', params.shipper.trim());
    }
    if (params.vessel && params.vessel.trim()) {
      queryParams.append('vessel', params.vessel.trim());
    }
    if (params.transportType && params.transportType.trim()) {
      queryParams.append('transportType', params.transportType.trim());
    }

    const response = await api.get(`/billoflading?${queryParams.toString()}`);

    // Handle both paginated and legacy responses
    if (response.data.items && response.data.pagination) {
      // Paginated response - process items for each bill
      const bills = await enrichBillsWithItems(response.data.items);

      return {
        items: bills,
        pagination: response.data.pagination
      };
    } else {
      // Legacy response (shouldn't happen with pagination backend)
      console.warn('Received legacy response format, expected paginated response');
      const bills = await enrichBillsWithItems(response.data as any[]);

      return {
        items: bills,
        pagination: {
          currentPage: 1,
          pageSize: bills.length,
          totalItems: bills.length,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false
        }
      };
    }
  } catch (error) {
    console.error('[BillOfLadingService] Failed to fetch paginated bills of lading:', error);
    throw error;
  }
};

export const create = async (
  data: Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<BillOfLading> => {
  try {
    const payload = {
      ...data,
      items: data.items,
    };
    const response = await api.post('/billoflading', payload);
    return response.data;
  } catch (error) {
    console.error('[BillOfLadingService] Failed to create bill of lading:', error);
    throw error;
  }
};



export const update = async (
  id: string,
  data: Omit<BillOfLading, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<BillOfLading> => {
  try {
    // ✅ CHANGED: Include items in PUT request instead of excluding them
    const response = await api.put(`/billoflading/${id}`, data);
    return response.data;
  } catch (error) {
    console.error(`[BillOfLadingService] Failed to update bill of lading ${id}:`, error);
    throw error;
  }
};

export type ItemDiffPayload = {
  newItems: Array<Record<string, any>>;
  updatedItems: Array<{ id: string } & Record<string, any>>;
  removedItemIds: string[];
};

export const applyItemsDiff = async (
  billOfLadingId: string,
  payload: ItemDiffPayload,
): Promise<any> => {
  try {
    const response = await api.post(
      `/billoflading/${encodeURIComponent(billOfLadingId)}/items:apply-diff`,
      payload,
    );
    return response.data;
  } catch (error) {
    console.error(`[BillOfLadingService] Failed to apply items diff for BOL ${billOfLadingId}:`, error);
    throw error;
  }
};

export const bulkImport = async (data: any[]): Promise<{ success: number; errors: string[] }> => {
  try {
    const response = await api.post('/billoflading/bulk', data);
    return response.data;
  } catch (error) {
    console.error('[BillOfLadingService] Failed to bulk import bills of lading:', error);
    throw error;
  }
};

const remove = async (id: string): Promise<void> => {
  try {
    await api.delete(`/billoflading/${id}`);
  } catch (error) {
    console.error(`[BillOfLadingService] Failed to delete bill of lading ${id}:`, error);
    throw error;
  }
};

export { remove as delete };

export default {
  list,
  fetchPaginated,
  create,
  update,
  delete: remove,
  updateTransport,
  refreshTransport,
  mapVisitToSnapshot,
  applyItemsDiff,
  bulkImport,
};
