import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useThirdParty } from '../src/composables/use.third-party';

const post = vi.fn();
const get = vi.fn().mockResolvedValue({ data: [] });
const axios = { post, get } as any;

vi.mock('vue', async (orig) => {
  const actual: any = await orig();
  return { ...actual, inject: () => axios };
});

describe('useThirdParty.createMinimal', () => {
  beforeEach(() => {
    post.mockReset();
    get.mockReset();
    get.mockResolvedValue({ data: [] });
  });

  it('POSTs the minimal payload and appends the returned entity to thirdParties', async () => {
    post.mockResolvedValueOnce({
      data: { id: 'new', companyName: 'New Co', industryType: 'Shipping Line', companyAddress: 'X' },
    });
    const store = useThirdParty();
    const created = await store.createMinimal({
      companyName: 'New Co',
      industryType: 'Shipping Line',
      companyAddress: 'X',
    });
    expect(post).toHaveBeenCalledWith('/third-party', expect.objectContaining({
      companyName: 'New Co',
      industryType: 'Shipping Line',
      companyAddress: 'X',
    }));
    expect(created.id).toBe('new');
    expect(store.thirdParties.value).toContainEqual(expect.objectContaining({ id: 'new' }));
  });
});
