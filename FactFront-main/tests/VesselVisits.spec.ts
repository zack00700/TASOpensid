import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import VesselVisits from '../src/components/VesselVisits.vue';
import type { VesselVisit } from '../src/types/vessel-visit';
import { i18n } from '../src/i18n';

vi.mock('../src/composables/use.third-party', () => ({
  useThirdParty: () => ({ thirdParties: ref([]), createMinimal: vi.fn() }),
}));
vi.mock('../src/stores/authStore', () => ({
  useAuthStore: () => ({ isAdmin: () => false }),
}));

const routerPushMock = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushMock }),
  useRoute: () => ({ query: {} }),
  RouterLink: { template: '<a><slot /></a>' },
}));

function makeVisit(id: string, name: string): VesselVisit {
  return {
    id,
    vesselName: name,
    vesselId: 'IMO' + id,
    visitReference: 'REF-' + id,
    phase: 'Active',
    service: 'WCCA',
    serviceName: 'West Coast',
    facility: 'Terminal A',
    eta: '2026-05-10T08:00',
    etd: '2026-05-11T08:00',
    ata: '',
    atd: '',
    pod: 'FRLEH',
    pol: 'USNYC',
    finalDestination: '',
    beginReceive: '',
    dryCutoff: '',
    reeferCutoff: '',
    hazCutoff: '',
    emptyPickup: '',
    inboundVoyage: '',
    outboundVoyage: '',
    inboundCaptain: '',
    outboundCaptain: '',
    lineOperator: '',
    notes: '',
  };
}

const visitsFixture = [makeVisit('a', 'MV Alpha'), makeVisit('b', 'MV Bravo')];

const axiosMock = {
  get: vi.fn().mockResolvedValue({ data: visitsFixture }),
  post: vi.fn().mockResolvedValue({ data: {} }),
  put: vi.fn().mockResolvedValue({ data: {} }),
};

function mountPage() {
  return mount(VesselVisits, {
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
      stubs: {
        AdvancedFilter: true,
        PageHeader: true,
        SearchInput: true,
        Button: true,
        Teleport: true,
      },
    },
  });
}

describe('VesselVisits — selection + edit toolbar', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.put.mockReset();
    axiosMock.get.mockResolvedValue({ data: visitsFixture });
    axiosMock.put.mockResolvedValue({ data: {} });
  });

  it('renders rows with no selection on initial mount, edit button disabled', async () => {
    const wrapper = mountPage();
    await flushPromises();

    const rows = wrapper.findAll('tbody tr');
    expect(rows.length).toBe(2);
    rows.forEach((r) => expect(r.classes().some((c) => c.includes('ring-blue-500'))).toBe(false));

    const editButton = wrapper.find('[data-test="toolbar-edit-details"]');
    expect(editButton.exists()).toBe(true);
    expect(editButton.attributes('disabled')).toBeDefined();
  });

  it('clicking a row selects it and enables visit-scoped toolbar buttons', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.findAll('tbody tr')[0].trigger('click');
    await wrapper.vm.$nextTick();

    const firstRow = wrapper.findAll('tbody tr')[0];
    expect(firstRow.classes().some((c) => c.includes('ring-blue-500'))).toBe(true);

    const editButton = wrapper.find('[data-test="toolbar-edit-details"]');
    expect(editButton.attributes('disabled')).toBeUndefined();
  });

  it('clicking the same row again deselects it', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const row = () => wrapper.findAll('tbody tr')[0];

    await row().trigger('click');
    await wrapper.vm.$nextTick();
    expect(row().classes().some((c) => c.includes('ring-blue-500'))).toBe(true);

    await row().trigger('click');
    await wrapper.vm.$nextTick();
    expect(row().classes().some((c) => c.includes('ring-blue-500'))).toBe(false);
  });

  it('clicking a different row swaps the selection', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.findAll('tbody tr')[0].trigger('click');
    await wrapper.vm.$nextTick();
    await wrapper.findAll('tbody tr')[1].trigger('click');
    await wrapper.vm.$nextTick();

    const rows = wrapper.findAll('tbody tr');
    expect(rows[0].classes().some((c) => c.includes('ring-blue-500'))).toBe(false);
    expect(rows[1].classes().some((c) => c.includes('ring-blue-500'))).toBe(true);
  });

  it('preview Eye click does not select the row', async () => {
    const wrapper = mountPage();
    await flushPromises();

    const previewBtn = wrapper.findAll('tbody tr')[0].find('[data-test="row-preview"]');
    expect(previewBtn.exists()).toBe(true);
    await previewBtn.trigger('click');
    await wrapper.vm.$nextTick();

    const firstRow = wrapper.findAll('tbody tr')[0];
    expect(firstRow.classes().some((c) => c.includes('ring-blue-500'))).toBe(false);
  });

  it('clicking Edit Details with a selection opens the form in edit mode', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.findAll('tbody tr')[0].trigger('click');
    await wrapper.vm.$nextTick();

    await wrapper.find('[data-test="toolbar-edit-details"]').trigger('click');
    await wrapper.vm.$nextTick();

    const form = wrapper.findComponent({ name: 'VesselVisitForm' });
    expect(form.exists()).toBe(true);
    expect(form.props('editMode')).toBe(true);
    expect(form.props('initialData')).toMatchObject({ id: 'a', vesselName: 'MV Alpha' });
  });

  it('on form submit emit, the parent refetches visits via GET /visit', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.findAll('tbody tr')[0].trigger('click');
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-test="toolbar-edit-details"]').trigger('click');
    await wrapper.vm.$nextTick();

    axiosMock.get.mockClear();
    const updated = makeVisit('a', 'MV Alpha (renamed)');
    axiosMock.get.mockResolvedValueOnce({ data: [updated, visitsFixture[1]] });

    const form = wrapper.findComponent({ name: 'VesselVisitForm' });
    form.vm.$emit('submit', updated);
    await flushPromises();

    expect(axiosMock.get).toHaveBeenCalledTimes(1);
    // form is closed
    expect(wrapper.findComponent({ name: 'VesselVisitForm' }).exists()).toBe(false);
    // selection re-resolved by id, highlight stays on the same row (now showing new name)
    expect(wrapper.text()).toContain('MV Alpha (renamed)');
  });
});

const writeFileMock = vi.fn();
vi.mock('xlsx', () => ({
  utils: {
    book_new: () => ({}),
    json_to_sheet: () => ({}),
    book_append_sheet: () => {},
  },
  writeFile: (...args: unknown[]) => writeFileMock(...args),
}));

describe('VesselVisits — export toolbar', () => {
  beforeEach(() => {
    writeFileMock.mockReset();
    axiosMock.get.mockReset();
    axiosMock.get.mockResolvedValue({ data: visitsFixture });
  });

  it('clicking Export triggers an XLSX download with date-stamped filename', async () => {
    const wrapper = mountPage();
    await flushPromises();

    const exportBtn = wrapper.find('[data-test="toolbar-export"]');
    await exportBtn.trigger('click');
    await flushPromises();

    expect(writeFileMock).toHaveBeenCalledOnce();
    const filenameArg = writeFileMock.mock.calls[0][1] as string;
    expect(filenameArg).toMatch(/^vessel-visits-\d{4}-\d{2}-\d{2}\.xlsx$/);
  });

  it('falls back to CSV when XLSX writeFile throws', async () => {
    writeFileMock.mockImplementationOnce(() => {
      throw new Error('xlsx unavailable');
    });
    const createObjectURL = vi.fn(() => 'blob:mock-url');
    const revokeObjectURL = vi.fn();
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    (URL as any).createObjectURL = createObjectURL;
    (URL as any).revokeObjectURL = revokeObjectURL;

    try {
      const wrapper = mountPage();
      await flushPromises();
      await wrapper.find('[data-test="toolbar-export"]').trigger('click');
      await flushPromises();

      expect(writeFileMock).toHaveBeenCalledOnce();
      expect(createObjectURL).toHaveBeenCalledOnce();
      const blobArg = createObjectURL.mock.calls[0][0] as Blob;
      expect(blobArg.type).toContain('text/csv');
    } finally {
      (URL as any).createObjectURL = originalCreate;
      (URL as any).revokeObjectURL = originalRevoke;
    }
  });

  it('with empty visits, does not download and leaves the button enabled', async () => {
    axiosMock.get.mockResolvedValue({ data: [] });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-test="toolbar-export"]').trigger('click');
    await flushPromises();

    expect(writeFileMock).not.toHaveBeenCalled();
    const btn = wrapper.find('[data-test="toolbar-export"]');
    expect(btn.attributes('disabled')).toBeUndefined();
  });
});

describe('VesselVisits — statistics toolbar', () => {
  beforeEach(() => {
    routerPushMock.mockReset();
    axiosMock.get.mockReset();
    axiosMock.get.mockResolvedValue({ data: visitsFixture });
  });

  it('clicking Statistics navigates to /vessels/statistics, with ?visit=<id> when a visit is selected', async () => {
    const wrapper = mountPage();
    await flushPromises();

    // No selection → push with no query
    await wrapper.find('[data-test="toolbar-statistics"]').trigger('click');
    expect(routerPushMock).toHaveBeenLastCalledWith({ path: '/vessels/statistics', query: undefined });

    // Select a row → push with ?visit=<id>
    await wrapper.findAll('tbody tr')[0].trigger('click');
    await wrapper.find('[data-test="toolbar-statistics"]').trigger('click');
    const last = routerPushMock.mock.calls.at(-1)![0] as { path: string; query?: Record<string, string> };
    expect(last.path).toBe('/vessels/statistics');
    expect(last.query?.visit).toBeTruthy();
  });
});

describe('VesselVisits — record event toolbar', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.get.mockResolvedValue({ data: visitsFixture });
  });

  it('clicking Record Event with a selected visit opens the modal', async () => {
    const wrapper = mountPage();
    await flushPromises();

    // Select a row first
    await wrapper.findAll('tbody tr')[0].trigger('click');
    await flushPromises();

    // Click Record Event
    await wrapper.find('[data-test="toolbar-record-event"]').trigger('click');
    await flushPromises();

    // Modal mounted
    expect(wrapper.findComponent({ name: 'RecordVesselEventModal' }).exists()).toBe(true);
  });
});
