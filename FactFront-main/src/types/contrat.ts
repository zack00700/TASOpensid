
import { EventConfig } from "./event-config";

export type ContractSatus = 'Active' | 'Disable';
export type CalculationModeType = 'Date' | 'Quantity' | 'DateByTEU' | 'Special' | 'Tiered' | 'Banded';
export type ServiceType = 'STORAGE' | 'HANDLING' | 'THC' | 'DEMURRAGE' | 'DETENTION' | 'CLEANING' | 'INSPECTION' | 'WEIGHING' | 'SCANNING' | 'REEFER' | 'HAZMAT' | 'OOG' | 'ADMIN' | 'CUSTOMS' | 'OTHER';
export type RateType = 'SIMPLE' | 'TIERED' | 'BANDED' | 'VOLUME' | 'CUSTOM';

export interface Contract {
    id: string,
    name: string,
    description: string,
    calculationMode: CalculationMode;
    status: ContractSatus,
    startDate: Date,
    endDate: Date,
    // N4 extensions
    tariffId?: string,
    customerId?: string,
    customerName?: string,
    priority?: number,
    addendums?: ContractAddendum[]
}

export interface ContractAddendum {
    addendumId: string,
    description?: string,
    validFrom?: string,
    validTo?: string,
    rateOverrides?: RateManagementExtended[],
    createdBy?: string,
    createdAt?: string
}

export interface CalculationMode {
    type: CalculationModeType,
    subType: string,
    eventConfig: EventConfig,
    parameters: {
        gracePeriod: 0,
        minimumDays: 1
    }
}

export interface RateManagementExtended {
    rateId?: string,
    startQuantity?: number,
    endQuantity?: number,
    unitOfMeasurement?: string,
    amount?: number,
    flatCost?: number,
    currency?: string,
    startDate?: string,
    endDate?: string,
    isDefaultRate?: boolean,
    priority?: number,
    rateType?: RateType,
    glCode?: string,
    minAmount?: number,
    maxAmount?: number,
    quantityDivisor?: number
}

export interface Tariff {
    id?: string,
    name: string,
    description?: string,
    serviceType?: ServiceType,
    status?: ContractSatus,
    startDate?: Date,
    endDate?: Date,
    calculationMode?: CalculationMode,
    rates?: RateManagementExtended[],
    notes?: string
}
