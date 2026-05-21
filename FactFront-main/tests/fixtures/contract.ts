import type { Contract, CalculationModeType, RateManagementExtended } from '../../src/types/contrat';
import type { EventConfig } from '../../src/types/event-config';

export function makeEvent(over: Partial<EventConfig> = {}): EventConfig {
  return {
    id: 'evt-default',
    eventName: 'Storage',
    eventType: 'IN',
    billedEvent: false,
    ...over,
  } as EventConfig;
}

export function makeContract(over: Partial<Contract> = {}): Contract {
  return {
    id: 'c-default',
    name: 'Test Contract',
    description: '',
    status: 'Active',
    startDate: new Date('2026-01-01'),
    endDate: new Date('2026-12-31'),
    calculationMode: {
      type: 'Quantity',
      subType: '',
      eventConfig: makeEvent(),
      parameters: { gracePeriod: 0, minimumDays: 1 },
    },
    priority: 0,
    customerId: undefined,
    customerName: undefined,
    tariffId: undefined,
    addendums: [],
    ...over,
  } as Contract;
}

export function makeRate(over: Partial<RateManagementExtended> = {}): RateManagementExtended {
  return {
    rateId: `r-${Math.random().toString(36).slice(2, 8)}`,
    startQuantity: 0,
    endQuantity: 100,
    unitOfMeasurement: 'TEU',
    amount: 50,
    currency: 'EUR',
    isDefaultRate: false,
    priority: 0,
    rateType: 'SIMPLE',
    ...over,
  };
}

export function makeContractFormState(over: Record<string, unknown> = {}) {
  return {
    name: 'Test Contract',
    description: '',
    status: 'Active',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    calculationMode: {
      type: 'Quantity' as CalculationModeType,
      subType: '',
      eventConfig: makeEvent(),
      parameters: { gracePeriod: 0, minimumDays: 1 },
    },
    priority: 0,
    customerId: '',
    customerName: '',
    tariffId: '',
    rates: [] as RateManagementExtended[],
    ...over,
  };
}
