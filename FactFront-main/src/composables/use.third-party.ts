import { inject, ref, onMounted, watch } from "vue";
import type { AxiosInstance } from "axios";
import { ThirdParty, JsonPatchOperation } from "../types/third-party";

/**
 * Ensure a single shared instance across components so that updates
 * performed in one component propagate to all others.
 */
let sharedState: ReturnType<typeof createThirdPartyStore> | null = null;

export function useThirdParty() {
    if (!sharedState) {
        const $axios = inject<AxiosInstance>('$axios') as AxiosInstance;
        sharedState = createThirdPartyStore($axios);
    }
    return sharedState;
}

function createThirdPartyStore($axios: AxiosInstance) {
    const formData = ref<Omit<ThirdParty, 'id' | 'createdAt' | 'updatedAt'>>({
        version: 0,
        fullName: '',
        jobTitle: '',
        contactNumber: '',
        email: '',
        companyName: '',
        companyAddress: '',
        industryType: '',
        companyContactPerson: '',
        companyContactEmail: '',
        accessType: '',
        modulesRequired: [],
        identificationType: '',
        identificationNumber: ''
    });

    const errors = ref<Record<string, string>>({});
    const thirdParties = ref<ThirdParty[]>([]);
    const pendingPatch = ref<JsonPatchOperation[]>([]);
    const originalData = ref<ThirdParty | null>(null);
    const currentViewId = ref<string | null>(null);

    const validateForm = (currentId?: string) => {
        errors.value = {};
        let isValid = true;

        // Personal Information Validation
        if (!formData.value.fullName) {
            errors.value.fullName = "Full name is required";
            isValid = false;
        } else if (thirdParties.value.some(u => u.fullName === formData.value.fullName && u.id !== currentId)) {
            errors.value.fullName = "Full name must be unique";
            isValid = false;
        }

        if (!formData.value.contactNumber) {
            errors.value.contactNumber = "Contact number is required";
            isValid = false;
        } else if (!/^[+\d][\d\s().-]{5,19}$/.test(formData.value.contactNumber.trim())) {
            // Accept E.164-ish phone numbers: optional leading +, digits and common
            // separators ( -, ., (, ), space ), 6–20 chars total.
            errors.value.contactNumber = "Contact number must contain digits only (with optional + and separators)";
            isValid = false;
        }

        if (!formData.value.email) {
            errors.value.email = "Email is required";
            isValid = false;
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.value.email)) {
            errors.value.email = "Invalid email format";
            isValid = false;
        }

        // Company Information Validation
        if (!formData.value.companyName) {
            errors.value.companyName = "Company name is required";
            isValid = false;
        }

        if (!formData.value.companyAddress) {
            errors.value.companyAddress = "Company address is required";
            isValid = false;
        }

        if (formData.value.companyContactPerson && !formData.value.companyContactEmail) {
            errors.value.companyContactEmail =
            "Contact email is required when contact person is specified";
            isValid = false;
        }

        // Access Information Validation
        if (!formData.value.accessType) {
            errors.value.accessType = "Access type is required";
            isValid = false;
        }

        if (formData.value.modulesRequired.length === 0) {
            errors.value.modulesRequired = "At least one module must be selected";
            isValid = false;
        }

        // Security and Compliance Validation
        if (!formData.value.identificationType) {
            errors.value.identificationType = "Identification type is required";
            isValid = false;
        }

        if (!formData.value.identificationNumber) {
            errors.value.identificationNumber = "Identification number is required";
            isValid = false;
        }

        return isValid;
    };

    async function addThirdParty() {
        try {
            await $axios.post('/third-party', formData.value)
            await fetchThirdParties();
        } catch (exception) {
            console.error(exception)
        }
    }

    async function updateThirdParty(id: string) {
        try {
            await $axios.put(`/third-party/${id}`, formData.value)
            await fetchThirdParties();
        } catch (exception) {
            console.error(exception)
        }
    }

    function buildPatch(original: ThirdParty, updated: Omit<ThirdParty, 'id'>): JsonPatchOperation[] {
        const ops: JsonPatchOperation[] = [];
        Object.keys(updated).forEach((key) => {
            const k = key as keyof ThirdParty;
            if (updated[k as keyof typeof updated] !== original[k]) {
                ops.push({ op: 'replace', path: `/${k}`, value: updated[k as keyof typeof updated] });
            }
        });
        return ops;
    }

    async function patchThirdParty(id: string, version: number) {
        try {
            await $axios.patch(
                `/third-party/${id}`,
                pendingPatch.value,
                {
                    headers: {
                        'Content-Type': 'application/json-patch+json',
                        'If-Match': version.toString(),
                    },
                },
            );
            pendingPatch.value = [];
            await fetchThirdParties();
            originalData.value = { ...formData.value, id } as ThirdParty;
        } catch (exception: unknown) {
            const axiosErr = exception as Error & { response?: { status: number } };
            if (axiosErr?.response?.status === 409) {
                alert('Conflit – recharger ou fusionner');
            } else {
                console.error(exception);
            }
        }
    }

    async function validateThirdParty() {
        try {
            await $axios.post('/third-party/validate', formData.value)
        } catch (exception) {
            console.error(exception)
        }
    }

    type MinimalThirdParty = Pick<ThirdParty, 'companyName' | 'industryType' | 'companyAddress'> &
        Partial<ThirdParty>;

    async function createMinimal(payload: MinimalThirdParty): Promise<ThirdParty> {
        const body = {
            fullName: '',
            jobTitle: '',
            contactNumber: '',
            email: '',
            companyContactPerson: '',
            companyContactEmail: '',
            accessType: '',
            modulesRequired: [],
            identificationType: '',
            identificationNumber: '',
            ...payload,
        };
        const response = await $axios.post('/third-party', body);
        const created = response.data as ThirdParty;
        thirdParties.value = [...thirdParties.value, created];
        return created;
    }

    watch(
        formData,
        (newVal) => {
            if (originalData.value) {
                pendingPatch.value = buildPatch(originalData.value, newVal);
            }
        },
        { deep: true }
    );

    async function fetchThirdParties() {
        try {
            const response = await $axios.get('/third-party');
            thirdParties.value = response.data;
        } catch (exception) {
            console.error('Failed to fetch third parties:', exception);
        }
    }

    onMounted(() => {
        fetchThirdParties();
    });

    return {
        formData,
        errors,
        validateForm,
        addThirdParty,
        updateThirdParty,
        patchThirdParty,
        validateThirdParty,
        fetchThirdParties,
        createMinimal,
        pendingPatch,
        thirdParties,
        originalData,
        currentViewId
    }
}
