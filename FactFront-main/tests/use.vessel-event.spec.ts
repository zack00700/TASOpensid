import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useVesselEvent } from '../src/composables/use.vessel-event';

const $axios = { get: vi.fn(), post: vi.fn() };

vi.mock('vue', async (orig) => {
  const actual: any = await orig();
  return { ...actual, inject: () => $axios };
});

describe('useVesselEvent', () => {
  beforeEach(() => {
    $axios.get.mockReset();
    $axios.post.mockReset();
  });

  it('getVesselEvents calls GET /visit/{id}/event and stores the list', async () => {
    $axios.get.mockResolvedValueOnce({
      data: [{ id: 'e1', visitId: 'v1', eventId: 'cfg1', eventDate: '2026-05-14T14:30:00Z', notes: 'note' }],
    });
    const store = useVesselEvent();
    await store.getVesselEvents('v1');
    expect($axios.get).toHaveBeenCalledWith('/visit/v1/event');
    expect(store.events.value.length).toBe(1);
    expect(store.events.value[0].notes).toBe('note');
  });

  it('addVesselEvent POSTs to /visit/{id}/event and returns the response data', async () => {
    $axios.post.mockResolvedValueOnce({ data: { id: 'e1', message: 'Vessel event recorded' } });
    const store = useVesselEvent();
    const result = await store.addVesselEvent('v1', {
      eventId: 'cfg1',
      eventDate: '2026-05-14T14:30:00Z',
      notes: 'Pilot embarked',
    });
    expect($axios.post).toHaveBeenCalledWith('/visit/v1/event', {
      eventId: 'cfg1',
      eventDate: '2026-05-14T14:30:00Z',
      notes: 'Pilot embarked',
    });
    expect(result.id).toBe('e1');
  });
});
