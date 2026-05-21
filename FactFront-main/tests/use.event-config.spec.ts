import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useEventConfig } from '../src/composables/use.event-config';

const $axios = { get: vi.fn(), post: vi.fn() };

vi.mock('vue', async (orig) => {
  const actual: any = await orig();
  return { ...actual, inject: () => $axios };
});

describe('useEventConfig', () => {
  beforeEach(() => {
    $axios.get.mockReset();
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
});
