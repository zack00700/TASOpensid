import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import HoldsModal from '../src/components/HoldsModal.vue';
import type { Hold } from '../src/types/hold';
import { i18n } from '../src/i18n';

function makeVisit() {
  return {
    id: 'v-1',
    vesselName: 'MV Alpha',
    vesselId: 'IMO123',
    visitReference: 'REF-1',
    phase: 'Active' as const,
    service: 'WCCA',
    serviceName: 'West Coast',
    facility: 'Terminal A',
    eta: '', etd: '', ata: '', atd: '',
    pod: '', pol: '', finalDestination: '',
    beginReceive: '', dryCutoff: '', reeferCutoff: '', hazCutoff: '', emptyPickup: '',
    inboundVoyage: '', outboundVoyage: '', inboundCaptain: '', outboundCaptain: '',
    lineOperator: '', notes: '',
  };
}

function makeHold(overrides: Partial<Hold> = {}): Hold {
  return {
    id: 'h-1',
    visitId: 'v-1',
    type: 'Customs',
    reason: 'Awaiting clearance',
    openedAt: '2026-05-20T08:00:00Z',
    openedBy: 'alice',
    releasedAt: null,
    releasedBy: null,
    releaseNotes: null,
    active: true,
    ...overrides,
  };
}

const axiosMock = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
};

function mountModal(open = true, holds: Hold[] = []) {
  axiosMock.get.mockResolvedValue({ data: holds });
  return mount(HoldsModal, {
    props: { open, visit: makeVisit() },
    global: { plugins: [i18n], provide: { $axios: axiosMock } },
  });
}

describe('HoldsModal', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.post.mockReset();
    axiosMock.patch.mockReset();
    axiosMock.get.mockResolvedValue({ data: [] });
    axiosMock.post.mockResolvedValue({ data: makeHold() });
    axiosMock.patch.mockResolvedValue({ data: { ...makeHold(), active: false } });
  });

  it('renders the empty state when the visit has no holds', async () => {
    const wrapper = mountModal();
    await flushPromises();
    expect(wrapper.find('[data-test="holds-empty"]').exists()).toBe(true);
  });

  it('renders one row per hold with active badge', async () => {
    const wrapper = mountModal(true, [
      makeHold({ id: 'h1', reason: 'first' }),
      makeHold({ id: 'h2', reason: 'second', active: false, releasedAt: '2026-05-22T10:00:00Z', releasedBy: 'bob' }),
    ]);
    await flushPromises();

    expect(wrapper.find('[data-test="hold-row-h1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="hold-row-h2"]').exists()).toBe(true);
    // Active hold has a Release button; released one does not.
    expect(wrapper.find('[data-test="hold-release-h1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="hold-release-h2"]').exists()).toBe(false);
  });

  it('Add submit POSTs the new hold and refreshes the list', async () => {
    const wrapper = mountModal();
    await flushPromises();

    await wrapper.find<HTMLSelectElement>('[data-test="hold-add-type"]').setValue('Operational');
    await wrapper.find<HTMLTextAreaElement>('[data-test="hold-add-reason"]').setValue('  No berth available  ');
    // Mock the post + the subsequent refresh GET.
    axiosMock.post.mockResolvedValueOnce({ data: makeHold({ type: 'Operational', reason: 'No berth available' }) });
    axiosMock.get.mockResolvedValueOnce({ data: [makeHold({ type: 'Operational', reason: 'No berth available' })] });

    await wrapper.find('[data-test="hold-add-submit"]').trigger('click');
    await flushPromises();

    expect(axiosMock.post).toHaveBeenCalledWith(
      'visit/v-1/holds',
      { type: 'Operational', reason: 'No berth available' },
    );
    // Refresh was called (once on mount + once after add).
    expect(axiosMock.get.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('Add submit is blocked when reason is blank', async () => {
    const wrapper = mountModal();
    await flushPromises();
    // reason defaults to ''
    const btn = wrapper.find<HTMLButtonElement>('[data-test="hold-add-submit"]');
    expect(btn.element.disabled).toBe(true);
    await btn.trigger('click');
    expect(axiosMock.post).not.toHaveBeenCalled();
  });

  it('Release flow: click Release -> confirm with notes -> PATCH and refresh', async () => {
    const wrapper = mountModal(true, [makeHold({ id: 'h7' })]);
    await flushPromises();

    await wrapper.find('[data-test="hold-release-h7"]').trigger('click');
    await flushPromises();

    const notes = wrapper.find<HTMLTextAreaElement>('[data-test="hold-release-notes-h7"]');
    expect(notes.exists()).toBe(true);
    await notes.setValue('Customs released');

    axiosMock.patch.mockResolvedValueOnce({ data: { ...makeHold({ id: 'h7' }), active: false } });
    axiosMock.get.mockResolvedValueOnce({ data: [{ ...makeHold({ id: 'h7' }), active: false }] });

    await wrapper.find('[data-test="hold-release-confirm-h7"]').trigger('click');
    await flushPromises();

    expect(axiosMock.patch).toHaveBeenCalledWith('holds/h7/release', { releaseNotes: 'Customs released' });
  });

  it('surfaces backend error messages into the modal', async () => {
    axiosMock.post.mockReset();
    axiosMock.post.mockRejectedValueOnce({ response: { data: { message: 'reason is required' } } });

    const wrapper = mountModal();
    await flushPromises();

    await wrapper.find<HTMLTextAreaElement>('[data-test="hold-add-reason"]').setValue('x');
    await wrapper.find('[data-test="hold-add-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-test="holds-error"]').text()).toContain('reason is required');
  });
});
