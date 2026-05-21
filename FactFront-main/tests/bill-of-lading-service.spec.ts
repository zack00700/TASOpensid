import { describe, it, expect, vi } from 'vitest';

const axiosMock = vi.hoisted(() => ({
  post: vi.fn(() => Promise.resolve({ data: { id: 'bl-id' } })),
  put: vi.fn(() => Promise.resolve({ data: {} })),
  get: vi.fn(() => Promise.resolve({ data: [] })),
  delete: vi.fn(() => Promise.resolve()),
}));

vi.mock('../src/plugin/axios', () => ({
  default: axiosMock,
}));

const itemServiceMock = vi.hoisted(() => ({
  create: vi.fn(),
  get: vi.fn((id: string) =>
    Promise.resolve({ id, type: 'container', itemNumber: `NUM-${id}`, status: 'Pending' }),
  ),
}));

vi.mock('../src/services/itemService', () => ({
  default: itemServiceMock,
}));

import billOfLadingService from '../src/services/billOfLadingService';
import api from '../src/plugin/axios';
import itemService from '../src/services/itemService';

describe('billOfLadingService', () => {
  beforeEach(() => {
    axiosMock.post.mockReset();
    axiosMock.post.mockImplementation(() => Promise.resolve({ data: { id: 'bl-id' } }));
    axiosMock.put.mockReset();
    axiosMock.put.mockImplementation(() => Promise.resolve({ data: {} }));
    axiosMock.get.mockReset();
    axiosMock.get.mockImplementation(() => Promise.resolve({ data: [] }));
    axiosMock.delete.mockReset();
    axiosMock.delete.mockImplementation(() => Promise.resolve());

    itemServiceMock.create.mockReset();
    itemServiceMock.get.mockReset();
    itemServiceMock.get.mockImplementation((id: string) =>
      Promise.resolve({ id, type: 'container', itemNumber: `NUM-${id}`, status: 'Pending' }),
    );
  });

  it('sends packagesNumber when creating a bill of lading', async () => {
    const bl = {
      blNumber: 'BL1',
      type: 'Original',
      status: 'Draft',
      shipper: 'Shipper',
      consignee: 'Consignee',
      notifyParty: '',
      transportType: 'Vessel',
      vessel: 'Vessel',
      voyage: 'Voyage',
      portOfLoading: 'POL',
      portOfDischarge: 'POD',
      placeOfDelivery: 'Place',
      driver: '',
      trainNumber: '',
      truckNumber: '',
      commodity: {
        description: 'Desc',
        weightKg: 1,
        volumeM3: 1,
        packagesNumber: 5,
        hazardous: false,
      },
      items: [],
    };

    await billOfLadingService.create(bl);
    expect(api.post).toHaveBeenCalledWith(
      '/billoflading',
      expect.objectContaining({
        commodity: expect.objectContaining({ packagesNumber: 5 }),
      }),
    );
  });

  it('sends items when creating a bill of lading', async () => {
    const bl = {
      blNumber: 'BL1',
      type: 'Original',
      status: 'Draft',
      shipper: 'Shipper',
      consignee: 'Consignee',
      notifyParty: '',
      transportType: 'Vessel',
      vessel: 'Vessel',
      voyage: 'Voyage',
      portOfLoading: 'POL',
      portOfDischarge: 'POD',
      placeOfDelivery: 'Place',
      driver: '',
      trainNumber: '',
      truckNumber: '',
      commodity: {
        description: 'Desc',
        weightKg: 1,
        volumeM3: 1,
        packagesNumber: 5,
        hazardous: false,
      },
      items: [
        { type: 'container', itemNumber: 'ABC123', status: 'Pending' },
      ],
    };

    await billOfLadingService.create(bl);
    expect(api.post).toHaveBeenCalledWith(
      '/billoflading',
      expect.objectContaining({
        items: [
          expect.objectContaining({
            type: 'container',
            itemNumber: 'ABC123',
            status: 'Pending',
          }),
        ],
      }),
    );
  });

  it('fetches items by id when listing bills of lading', async () => {
    (api.get as any).mockResolvedValueOnce({
      data: [
        {
          id: 'bl1',
          blNumber: 'BL1',
          type: 'Original',
          status: 'Draft',
          shipper: 'Shipper',
          consignee: 'Consignee',
          notifyParty: '',
          transportType: 'Vessel',
          vessel: 'Vessel',
          voyage: 'Voyage',
          portOfLoading: 'POL',
          portOfDischarge: 'POD',
          placeOfDelivery: 'Place',
          driver: '',
          trainNumber: '',
          truckNumber: '',
          commodity: {
            description: 'Desc',
            weightKg: 1,
            volumeM3: 1,
            packagesNumber: 5,
            hazardous: false,
          },
          itemIds: ['item1'],
        },
      ],
    });

    const bills = await billOfLadingService.list();

    expect(itemService.get).toHaveBeenCalledWith('item1');
    expect(bills[0].items[0]).toEqual(
      expect.objectContaining({ id: 'item1', itemNumber: 'NUM-item1' }),
    );
  });

  it('ensures bill of lading form items have string ids when retrieved', async () => {
    (api.get as any).mockResolvedValueOnce({
      data: [
        {
          id: 'bl1',
          blNumber: 'BL1',
          type: 'Original',
          status: 'Draft',
          shipper: 'Shipper',
          consignee: 'Consignee',
          notifyParty: '',
          transportType: 'Vessel',
          vessel: 'Vessel',
          voyage: 'Voyage',
          portOfLoading: 'POL',
          portOfDischarge: 'POD',
          placeOfDelivery: 'Place',
          driver: '',
          trainNumber: '',
          truckNumber: '',
          commodity: {
            description: 'Desc',
            weightKg: 1,
            volumeM3: 1,
            packagesNumber: 5,
            hazardous: false,
          },
          itemIds: ['mongo-id'],
        },
      ],
    });

    itemServiceMock.get.mockResolvedValueOnce(
      Promise.resolve({
        _id: { $oid: 'mongo-id' },
        type: 'container',
        itemNumber: 'NUM-mongo-id',
        status: 'Pending',
      }),
    );

    const bills = await billOfLadingService.list();

    expect(bills[0].items[0].id).toBe('mongo-id');
    expect(typeof bills[0].items[0].id).toBe('string');
  });

  it('updates transport with vessel visit', async () => {
    await billOfLadingService.updateTransport('123', {
      type: 'VESSEL',
      vesselVisitId: 'vv1',
    });
    expect(api.put).toHaveBeenCalledWith(
      '/billoflading/123/transport',
      { type: 'VESSEL', vesselVisitId: 'vv1' },
    );
  });

  it('refreshes transport snapshot', async () => {
    await billOfLadingService.refreshTransport('123');
    expect(api.put).toHaveBeenCalledWith(
      '/billoflading/123/transport/refresh',
    );
  });

  it('maps vessel visit to snapshot', () => {
    const snapshot = billOfLadingService.mapVisitToSnapshot({
      id: '1',
      vesselName: 'V',
      imo: '123',
      callSign: 'CS',
      voyageIn: 'I',
      voyageOut: 'O',
      operator: 'Op',
      port: 'P',
      terminal: 'T',
      berth: 'B',
      eta: '2024-01-01',
      etd: '2024-01-02',
      ata: '2024-01-03',
      atd: '2024-01-04',
    });
    expect(snapshot).toMatchObject({
      id: '1',
      vesselName: 'V',
      imo: '123',
      callSign: 'CS',
      voyageIn: 'I',
      voyageOut: 'O',
      operator: 'Op',
      port: 'P',
      terminal: 'T',
      berth: 'B',
      eta: '2024-01-01',
      etd: '2024-01-02',
      ata: '2024-01-03',
      atd: '2024-01-04',
    });
    expect(snapshot.capturedAt).toBeDefined();
  });
});
