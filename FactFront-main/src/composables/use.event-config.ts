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
            const response = await $axios.post('/event', formData.value);
            await getEventConfig();
            return response.data;
        } catch (exception) {
            console.error(exception);
            throw exception;
        }
    }

    async function updateEventConfig(id: string, data: Omit<EventConfig, "id">) {
        try {
            const response = await $axios.put(`/event/${id}`, { ...data, id });
            await getEventConfig();
            return response.data;
        } catch (exception) {
            console.error(exception);
            throw exception;
        }
    }

    async function deleteEventConfig(id: string) {
        try {
            await $axios.delete(`/event/${id}`);
            await getEventConfig();
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
