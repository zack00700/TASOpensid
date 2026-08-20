import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useEventConfig } from '../src/composables/use.event-config';

const $axios = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() };

vi.mock('vue', async (orig) => {
  const actual: any = await orig();
  return { ...actual, inject: () => $axios };
});

describe('useEventConfig', () => {
  beforeEach(() => {
    $axios.get.mockReset();
    $axios.post.mockReset();
    $axios.put.mockReset();
    $axios.delete.mockReset();
    $axios.get.mockResolvedValue({ data: [] });
  });

  it('getEventConfig with no arg calls GET /event with empty params', async () => {
    const store = useEventConfig();
    await store.getEventConfig();
    expect($axios.get).toHaveBeenCalledWith('/event', { params: {} });
  });

  it('getEventConfig with scope calls GET /event with the scope query', async () => {
    const store = useEventConfig();
    await store.getEventConfig('VESSEL');
    expect($axios.get).toHaveBeenCalledWith('/event', { params: { scope: 'VESSEL' } });
  });

  it('updateEventConfig PUTs /event/{id} and replaces the item in local state', async () => {
    const store = useEventConfig();
    store.eventConfigs.value = [
      { id: 'e1', eventName: 'Old', eventType: 'IN', billedEvent: false, scope: 'ITEM' },
      { id: 'e2', eventName: 'Other', eventType: 'OUT', billedEvent: true, scope: 'VESSEL' },
    ];
    const updated = { id: 'e1', eventName: 'New', eventType: 'OUT', billedEvent: true, scope: 'BOTH' };
    $axios.put.mockResolvedValueOnce({ data: updated });

    const payload = { eventName: 'New', eventType: 'OUT', billedEvent: true, scope: 'BOTH' as const };
    await store.updateEventConfig('e1', payload);

    expect($axios.put).toHaveBeenCalledWith('/event/e1', payload);
    expect(store.eventConfigs.value).toEqual([updated, store.eventConfigs.value![1]]);
  });

  it('updateEventConfig rethrows on API failure and leaves local state untouched', async () => {
    const store = useEventConfig();
    const before = [{ id: 'e1', eventName: 'Old', eventType: 'IN' as const, billedEvent: false, scope: 'ITEM' as const }];
    store.eventConfigs.value = [...before];
    $axios.put.mockRejectedValueOnce(new Error('boom'));

    await expect(
      store.updateEventConfig('e1', { eventName: 'X', eventType: 'IN', billedEvent: false, scope: 'ITEM' })
    ).rejects.toThrow('boom');
    expect(store.eventConfigs.value).toEqual(before);
  });

  it('deleteEventConfig DELETEs /event/{id} and removes the item from local state', async () => {
    const store = useEventConfig();
    store.eventConfigs.value = [
      { id: 'e1', eventName: 'A', eventType: 'IN', billedEvent: false, scope: 'ITEM' },
      { id: 'e2', eventName: 'B', eventType: 'OUT', billedEvent: true, scope: 'VESSEL' },
    ];
    $axios.delete.mockResolvedValueOnce({ status: 204 });

    await store.deleteEventConfig('e1');

    expect($axios.delete).toHaveBeenCalledWith('/event/e1');
    expect(store.eventConfigs.value!.map(e => e.id)).toEqual(['e2']);
  });

  it('deleteEventConfig rethrows on API failure and leaves local state untouched', async () => {
    const store = useEventConfig();
    const before = [{ id: 'e1', eventName: 'A', eventType: 'IN' as const, billedEvent: false, scope: 'ITEM' as const }];
    store.eventConfigs.value = [...before];
    $axios.delete.mockRejectedValueOnce(new Error('nope'));

    await expect(store.deleteEventConfig('e1')).rejects.toThrow('nope');
    expect(store.eventConfigs.value).toEqual(before);
  });
});
