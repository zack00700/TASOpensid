import { describe, it, expect, vi } from 'vitest';

const axiosMock = vi.hoisted(() => ({
  post: vi.fn(() => Promise.resolve({ data: { id: 'item-id' } })),
  get: vi.fn(() => Promise.resolve({ data: {} })),
}));

vi.mock('../src/plugin/axios', () => ({
  default: axiosMock,
}));

import itemService from '../src/services/itemService';
import api from '../src/plugin/axios';

describe('itemService', () => {
  beforeEach(() => {
    axiosMock.post.mockReset();
    axiosMock.post.mockImplementation(() => Promise.resolve({ data: { id: 'item-id' } }));
    axiosMock.get.mockReset();
    axiosMock.get.mockImplementation(() => Promise.resolve({ data: {} }));
  });

  it('uses singular endpoint when creating item', async () => {
    await itemService.create({
      type: 'container',
      itemNumber: 'ABC123',
      status: 'Pending',
      billOfLadingId: 'bl1',
    });
    expect(api.post).toHaveBeenCalledWith('/item', expect.any(Object));
  });

  it('normalizes item id from nested identifiers when retrieving', async () => {
    axiosMock.get.mockResolvedValueOnce({
      data: {
        _id: { $oid: '507f1f77bcf86cd799439011' },
        itemNumber: 'ITEM-001',
      },
    });

    const item = await itemService.get('507f1f77bcf86cd799439011');

    expect(item.id).toBe('507f1f77bcf86cd799439011');
    expect(typeof item.id).toBe('string');
  });
});
