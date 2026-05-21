import { describe, it, expect, vi } from 'vitest';
import { ref } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import RecordVesselEventModal from '../src/components/RecordVesselEventModal.vue';
import { i18n } from '../src/i18n';

const addVesselEvent = vi.fn();
const getVesselEvents = vi.fn();
const eventsRef = ref<any[]>([]);

vi.mock('../src/composables/use.vessel-event', () => ({
  useVesselEvent: () => ({
    events: eventsRef,
    getVesselEvents,
    addVesselEvent,
  }),
}));

const eventConfigsRef = ref<any[]>([
  { id: 'cfg-pilot', eventName: 'Pilot Boarded', eventType: 'INTERMEDIATE', billedEvent: false, scope: 'VESSEL' },
]);
const getEventConfig = vi.fn();
vi.mock('../src/composables/use.event-config', () => ({
  useEventConfig: () => ({
    eventConfigs: eventConfigsRef,
    getEventConfig,
    formData: ref({ eventName: '', eventType: 'IN', billedEvent: false }),
    isValid: ref(true),
    validateForm: () => true,
    addEventConfig: vi.fn(),
    errors: ref({}),
  }),
}));

function mountModal(visit: any = { id: 'v1', vesselName: 'MV Alpha' }) {
  return mount(RecordVesselEventModal, {
    global: { plugins: [i18n] },
    props: { open: true, visit },
  });
}

describe('RecordVesselEventModal', () => {
  it('renders the modal with the vessel name in the title and the events history section', () => {
    eventsRef.value = [];
    const w = mountModal();
    expect(w.text()).toContain('MV Alpha');
    expect(w.text()).toContain('Recent events');
    expect(w.text()).toContain('No events recorded yet');
  });

  it('renders the recent events list when events are present', () => {
    eventsRef.value = [
      { id: 'e1', visitId: 'v1', eventId: 'cfg-pilot', eventDate: '2026-05-14T14:30:00Z', notes: 'Pilot embarked' },
    ];
    const w = mountModal();
    expect(w.text()).toContain('Pilot embarked');
  });

  it('calls addVesselEvent on submit with the form payload', async () => {
    eventsRef.value = [];
    addVesselEvent.mockResolvedValueOnce({ id: 'e1', message: 'Vessel event recorded' });
    const w = mountModal();

    await w.find('[data-test="event-config-option-cfg-pilot"]').trigger('click');
    await w.find('textarea[name="notes"]').setValue('Pilot embarked at flood tide');
    await w.find('form').trigger('submit.prevent');
    await flushPromises();

    expect(addVesselEvent).toHaveBeenCalledWith('v1', expect.objectContaining({
      eventId: 'cfg-pilot',
      notes: 'Pilot embarked at flood tide',
    }));
  });
});
