import { inject, ref, onMounted } from "vue";
import type { AxiosInstance } from "axios";

import { VesselVisit } from "../types/vessel-visit";

export function useVesselVisit() {
    const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
    const errors = ref<Record<string, string>>({});
    const visits = ref<VesselVisit[]>([]);
    const formData = ref<VesselVisit>({
        vesselName: "",
        vesselId: "",
        visitReference: "",
        phase: "Active",
        service: "",
        serviceName: "",
        facility: "",
        eta: "",
        etd: "",
        ata: "",
        atd: "",
        pod: "",
        pol: "",
        finalDestination: "",
        beginReceive: "",
        dryCutoff: "",
        reeferCutoff: "",
        hazCutoff: "",
        emptyPickup: "",
        inboundVoyage: "",
        outboundVoyage: "",
        inboundCaptain: "",
        outboundCaptain: "",
        lineOperator: "",
        notes: "",
    });

    const validateForm = () => {
        errors.value = {};
        let isValid = true;

        if (!formData.value.vesselName) {
            errors.value.vesselName = "Vessel name is required";
            isValid = false;
        }

        if (!formData.value.vesselId) {
            errors.value.vesselId = "Vessel ID is required";
            isValid = false;
        }

        if (!formData.value.service) {
            errors.value.service = "Service is required";
            isValid = false;
        }

        if (!formData.value.eta) {
            errors.value.eta = "ETA is required";
            isValid = false;
        }

        if (!formData.value.etd) {
            errors.value.etd = "ETD is required";
            isValid = false;
        }

        if (new Date(formData.value.etd) <= new Date(formData.value.eta)) {
            errors.value.etd = "ETD must be after ETA";
            isValid = false;
        }

        return isValid;
    };

    async function addVesselVisit() {
        try {
            await $axios.post('visit', formData.value);
        } catch (exception) {
            console.error(exception);
        }
    }

    async function updateVesselVisit(id: string) {
        try {
            await $axios.put(`visit/${id}`, formData.value);
        } catch (exception) {
            console.error(exception);
        }
    }

    async function getVesselVisits() {
        try {
            const response = await $axios.get('visit');
            visits.value = response.data;
        } catch (exception) {
            console.error(exception);
        }
    }

    onMounted(() => {
        getVesselVisits();
    });

    return {
        errors,
        formData,
        visits,
        validateForm,
        addVesselVisit,
        updateVesselVisit,
        getVesselVisits,
    };
}
