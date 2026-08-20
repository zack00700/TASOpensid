import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import EventsConfiguration from '../src/components/EventsConfiguration.vue';
import { i18n } from '../src/i18n';

const events = [
  { id: 'evt-1', eventName: 'Gate-In', eventType: 'IN', billedEvent: true, scope: 'ITEM' },
  { id: 'evt-2', eventName: 'Vessel Berthing', eventType: 'INTERMEDIATE', billedEvent: false, scope: 'VESSEL' },
];

const axiosMock = {
  get: vi.fn(),
  post: vi.fn().mockResolvedValue({ status: 201, data: '' }),
  put: vi.fn(),
  delete: vi.fn().mockResolvedValue({ status: 204, data: '' }),
};

function mountPage() {
  axiosMock.get.mockResolvedValue({ data: events.map(e => ({ ...e })) });
  return mount(EventsConfiguration, {
    attachTo: document.body,
    global: {
      plugins: [i18n],
      provide: { $axios: axiosMock },
    },
  });
}

describe('EventsConfiguration — TC-01 regressions', () => {
  beforeEach(() => {
    axiosMock.get.mockReset();
    axiosMock.put.mockReset();
    axiosMock.delete.mockReset();
    axiosMock.post.mockClear();
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('Edit pre-fills the form with eventName + scope of the row (TC-01.4 main bug)', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-test="event-edit-evt-1"]').trigger('click');
    await flushPromises();

    const nameInput = wrapper.find<HTMLInputElement>('[data-test="event-form-name"]');
    const scopeSelect = wrapper.find<HTMLSelectElement>('[data-test="event-form-scope"]');
    expect(nameInput.element.value).toBe('Gate-In');
    expect(scopeSelect.element.value).toBe('ITEM');
  });

  it('Edit on a VESSEL-scoped event pre-fills scope=VESSEL (not stale)', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-test="event-edit-evt-2"]').trigger('click');
    await flushPromises();

    const nameInput = wrapper.find<HTMLInputElement>('[data-test="event-form-name"]');
    const scopeSelect = wrapper.find<HTMLSelectElement>('[data-test="event-form-scope"]');
    expect(nameInput.element.value).toBe('Vessel Berthing');
    expect(scopeSelect.element.value).toBe('VESSEL');
  });

  it('Confirming a delete calls DELETE /event/{id} and removes the row (TC-01.3)', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-test="event-delete-evt-1"]').trigger('click');
    await flushPromises();

    const confirmBtn = document.body.querySelector('[data-test="event-delete-confirm"]') as HTMLButtonElement | null;
    expect(confirmBtn).not.toBeNull();
    confirmBtn!.click();
    await flushPromises();

    expect(axiosMock.delete).toHaveBeenCalledWith('/event/evt-1');
    expect(wrapper.find('[data-test="event-row-evt-1"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="event-row-evt-2"]').exists()).toBe(true);
  });

  it('Saving an edit calls PUT /event/{id} with the full payload (TC-01.4 latent bug)', async () => {
    axiosMock.put.mockResolvedValueOnce({
      data: { id: 'evt-1', eventName: 'Gate-In Renamed', eventType: 'IN', billedEvent: true, scope: 'BOTH' },
    });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-test="event-edit-evt-1"]').trigger('click');
    await flushPromises();

    await wrapper.find<HTMLInputElement>('[data-test="event-form-name"]').setValue('Gate-In Renamed');
    await wrapper.find<HTMLSelectElement>('[data-test="event-form-scope"]').setValue('BOTH');
    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();

    expect(axiosMock.put).toHaveBeenCalledWith('/event/evt-1', {
      eventName: 'Gate-In Renamed',
      eventType: 'IN',
      billedEvent: true,
      scope: 'BOTH',
    });
  });

  it('Add resets scope to ITEM (no stale value from previous edit)', async () => {
    const wrapper = mountPage();
    await flushPromises();

    // First edit a VESSEL row, close form
    await wrapper.find('[data-test="event-edit-evt-2"]').trigger('click');
    await flushPromises();
    await wrapper.find('[aria-label="Close"]').trigger('click');
    await flushPromises();

    // Now click New Event
    const newBtn = wrapper.findAll('button').find(b => b.text().includes('New Event'));
    expect(newBtn).toBeDefined();
    await newBtn!.trigger('click');
    await flushPromises();

    const nameInput = wrapper.find<HTMLInputElement>('[data-test="event-form-name"]');
    const scopeSelect = wrapper.find<HTMLSelectElement>('[data-test="event-form-scope"]');
    expect(nameInput.element.value).toBe('');
    expect(scopeSelect.element.value).toBe('ITEM');
  });
});
