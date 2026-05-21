import { inject, onBeforeMount, ref } from 'vue';
import { Contract } from '../types/contrat';

export function useContract() {
    const $axios = inject('$axios');

    const contracts = ref<Contract | null>(null);

    async function getContracts() {
        try {
            const response = await $axios.get('/contract');
            contracts.value = response.data;
        } catch (error) {
            console.error(error);
        }
    }

    async function finalizeContract(contractId :string[]) {
        try {

            await $axios.put('/contract/' + contractId[0] + '/finalize')
            getContracts();
        } catch (error) {
            console.error(error);
        }
    }

    onBeforeMount(() => {
        getContracts();
    })

    return {
        contracts,
        finalizeContract
    }
}

