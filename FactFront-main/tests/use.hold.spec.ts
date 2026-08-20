import { describe, it, expect, vi, beforeEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useHold } from '../src/composables/use.hold';

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
};

function harness() {
  let api: ReturnType<typeof useHold> | null = null;
  const Comp = defineComponent({
    setup() {
      api = useHold();
      return () => h('div');
    },
  });
  mount(Comp, { global: { provide: { $axios: axiosMock } } });
  return api!;
}

describe('useHold', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.patch.mockReset();
    axiosMock.get.mockResolvedValue({ data: [] });
    axiosMock.post.mockResolvedValue({ data: {} });
    axiosMock.patch.mockResolvedValue({ data: {} });
  });

  it('listForVisit GETs visit/{id}/holds and stores results in holds ref', async () => {
    const api = harness();
    const sample = [
      { id: 'h1', visitId: 'v1', type: 'Customs', reason: 'x', openedAt: '2026-05-01', openedBy: 'a', releasedAt: null, releasedBy: null, releaseNotes: null, active: true },
    ];
    axiosMock.get.mockResolvedValueOnce({ data: sample });

    const result = await api.listForVisit('v1');

    expect(axiosMock.get).toHaveBeenCalledWith('visit/v1/holds');
    expect(result).toEqual(sample);
    expect(api.holds.value).toEqual(sample);
  });

  it('createHold POSTs visit/{id}/holds with {type, reason}', async () => {
    const api = harness();
    axiosMock.post.mockResolvedValueOnce({ data: { id: 'h2', active: true } });

    const created = await api.createHold('v1', { type: 'Operational', reason: 'No berth' });

    expect(axiosMock.post).toHaveBeenCalledWith('visit/v1/holds', { type: 'Operational', reason: 'No berth' });
    expect(created).toEqual({ id: 'h2', active: true });
  });

  it('releaseHold PATCHes holds/{id}/release with notes when provided', async () => {
    const api = harness();
    axiosMock.patch.mockResolvedValueOnce({ data: { id: 'h1', active: false } });

    await api.releaseHold('h1', '  Cleared by customs  ');

    expect(axiosMock.patch).toHaveBeenCalledWith('holds/h1/release', { releaseNotes: 'Cleared by customs' });
  });

  it('releaseHold sends an empty body when notes are blank', async () => {
    const api = harness();
    axiosMock.patch.mockResolvedValueOnce({ data: { id: 'h1', active: false } });

    await api.releaseHold('h1', '   ');

    expect(axiosMock.patch).toHaveBeenCalledWith('holds/h1/release', {});
  });

  it('releaseHold sends an empty body when notes omitted', async () => {
    const api = harness();
    axiosMock.patch.mockResolvedValueOnce({ data: { id: 'h1', active: false } });

    await api.releaseHold('h1');

    expect(axiosMock.patch).toHaveBeenCalledWith('holds/h1/release', {});
  });
});
