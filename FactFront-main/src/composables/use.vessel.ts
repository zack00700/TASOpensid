import { inject, onBeforeMount, watch, unref } from "vue";
import { ref, type Ref } from "vue";
import { Vessel } from "../types/vessel";

export function useVessel(initialData?: Ref<Vessel | null> | Vessel | null) {
    const $axios = inject('$axios');
    const formData = ref<Vessel>({
        name: '',
        imoNumber: '',
        mmsi: '',
        callSign: '',
        flag: '',
        owner: '',
        operator: '',
        vesselType: '',
        status: 'Active'
    });

    // Fonction pour réinitialiser le formulaire
    const resetForm = () => {
        formData.value = {
            name: '',
            imoNumber: '',
            mmsi: '',
            callSign: '',
            flag: '',
            owner: '',
            operator: '',
            vesselType: '',
            status: 'Active'
        };
    };

    // Fonction pour initialiser avec des données
    const initializeForm = (data: Vessel | null) => {
        if (data) {
            formData.value = { ...data };
        } else {
            resetForm();
        }
    };

    // Watcher pour les données initiales - gère les refs et les valeurs directes
    if (initialData !== undefined) {
        // Si c'est une ref, on observe ses changements
        if (typeof initialData === 'object' && 'value' in initialData) {
            watch(initialData, (newData) => {
                initializeForm(newData);
            }, { immediate: true });
        } else {
            // Si c'est une valeur directe, on l'initialise tout de suite
            initializeForm(initialData);
        }
    }

    const errors = ref<Record<string, string>>({});
    
    const validateForm = () => {
        errors.value = {};
        let isValid = true;

        if (!formData.value.name) {
            errors.value.name = "Vessel name is required";
            isValid = false;
        }

        if (!formData.value.imoNumber) {
            errors.value.imoNumber = "IMO number is required";
            isValid = false;
        } else if (!/^(IMO)?\d{7}$/i.test(formData.value.imoNumber)) {
            errors.value.imoNumber = "Invalid IMO number format (7 digits, with or without IMO prefix)";
            isValid = false;
        }

        if (formData.value.mmsi && !/^\d{9}$/.test(formData.value.mmsi)) {
            errors.value.mmsi = "MMSI must be exactly 9 digits";
            isValid = false;
        }

        if (!formData.value.callSign) {
            errors.value.callSign = "Call sign is required";
            isValid = false;
        }

        if (!formData.value.flag) {
            errors.value.flag = "Flag is required";
            isValid = false;
        }

        if (!formData.value.owner) {
            errors.value.owner = "Owner is required";
            isValid = false;
        }

        if (!formData.value.vesselType) {
            errors.value.vesselType = "Vessel type is required";
            isValid = false;
        }

        return isValid;
    };

    const vessels = ref<Vessel[]>([]);

    async function addVessel() {
        try {
            console.table(formData.value);
            await $axios.post('/vessel', formData.value);
        } catch (error) {
            console.error(error);
        }
    }

    async function updateVessel() {
        try {
            console.table(formData.value);
            await $axios.put('/vessel', formData.value);
        } catch (error) {
            console.error(error);
        }
    }

    async function getVessels() {
        try {
            const response = await $axios.get('/vessel');
            vessels.value = response.data;
        } catch (error) {
            console.error(error);
        }
    }

    async function deleteVessel(id: string): Promise<boolean> {
        try {
            await $axios.delete(`/vessel/${id}`);
            vessels.value = vessels.value.filter((v) => v.id !== id);
            return true;
        } catch (error) {
            console.error('deleteVessel failed', error);
            return false;
        }
    }

    onBeforeMount(() => {
        getVessels();
    });

    return {
        formData,
        errors,
        vessels,
        validateForm,
        addVessel,
        updateVessel,
        deleteVessel,
        getVessels,
        resetForm,
        initializeForm
    };
}