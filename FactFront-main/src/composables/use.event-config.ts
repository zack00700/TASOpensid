import { inject, onBeforeMount, ref } from "vue";
import { EventConfig, EventScope } from "../types/event-config";


export function useEventConfig() {
    const errors = ref<Record<string, string>>({});
    const $axios = inject('$axios');
    const eventConfigs = ref<EventConfig[] | null>([]);
    const formData = ref<Omit<EventConfig, "id">>({
        eventName: "",
        eventType: "IN",
        billedEvent: false,
        scope: "ITEM",
    });
    const isValid = ref(false);

    const validateForm = () => {
        errors.value = {};
        isValid.value = true;

        if (!formData.value.eventName) {
            errors.value.name = "Event name is required";
            isValid.value = false;
        }

        if (!formData.value.eventType) {
            errors.value.eventType = "Event type is required";
            isValid.value = false;
        }

        return isValid.value;
    };

    async function getEventConfig(scope?: EventScope) {
        try {
            const params: Record<string, string> = {};
            if (scope) params.scope = scope;
            const response = await $axios.get('/event', { params });
            eventConfigs.value = response.data;
        } catch (exception) {
            console.error(exception);
        }
    }

    async function addEventConfig() {
        try {
            await $axios.post('/event', formData.value)
        } catch (exception) {
            console.error(exception)
        }
    }

    async function updateEventConfig(id: string, data: Omit<EventConfig, "id">) {
        try {
            const response = await $axios.put(`/event/${id}`, data);
            const updated = response.data as EventConfig;
            if (eventConfigs.value) {
                const idx = eventConfigs.value.findIndex(e => e.id === id);
                if (idx !== -1) eventConfigs.value[idx] = updated;
            }
            return updated;
        } catch (exception) {
            console.error(exception);
            throw exception;
        }
    }

    async function deleteEventConfig(id: string) {
        try {
            await $axios.delete(`/event/${id}`);
            if (eventConfigs.value) {
                eventConfigs.value = eventConfigs.value.filter(e => e.id !== id);
            }
        } catch (exception) {
            console.error(exception);
            throw exception;
        }
    }

    onBeforeMount(() => {
        getEventConfig();
    })

    return {
        errors,
        formData,
        isValid,
        validateForm,
        addEventConfig,
        updateEventConfig,
        deleteEventConfig,
        getEventConfig,
        eventConfigs
    }
}
