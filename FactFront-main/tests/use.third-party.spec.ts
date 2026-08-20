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

describe('useThirdParty.validateForm — contactNumber (TC-03)', () => {
  function fillRequired(store: ReturnType<typeof useThirdParty>) {
    Object.assign(store.formData.value, {
      fullName: 'Jane Doe',
      email: 'jane@example.com',
      companyName: 'Acme',
      companyAddress: '1 rue de Paris',
      industryType: 'Shipping Line',
      accessType: 'Full Access',
      modulesRequired: ['Billing'],
      identificationType: 'National ID',
      identificationNumber: 'ABC123',
    });
  }

  it('rejects letters in contactNumber', () => {
    const store = useThirdParty();
    fillRequired(store);
    store.formData.value.contactNumber = '06AB12CD34';
    expect(store.validateForm()).toBe(false);
    expect(store.errors.value.contactNumber).toBeTruthy();
  });

  it('rejects empty contactNumber', () => {
    const store = useThirdParty();
    fillRequired(store);
    store.formData.value.contactNumber = '';
    expect(store.validateForm()).toBe(false);
    expect(store.errors.value.contactNumber).toBeTruthy();
  });

  it('rejects contactNumber with too few digits', () => {
    const store = useThirdParty();
    fillRequired(store);
    store.formData.value.contactNumber = '12345';
    expect(store.validateForm()).toBe(false);
    expect(store.errors.value.contactNumber).toBeTruthy();
  });

  it('accepts a plain digit string', () => {
    const store = useThirdParty();
    fillRequired(store);
    store.formData.value.contactNumber = '0612345678';
    expect(store.validateForm()).toBe(true);
    expect(store.errors.value.contactNumber).toBeUndefined();
  });

  it('accepts a formatted phone number with +, spaces, dashes, parens, dots', () => {
    const store = useThirdParty();
    fillRequired(store);
    store.formData.value.contactNumber = '+33 (1) 23-45.67 89';
    expect(store.validateForm()).toBe(true);
    expect(store.errors.value.contactNumber).toBeUndefined();
  });
});
