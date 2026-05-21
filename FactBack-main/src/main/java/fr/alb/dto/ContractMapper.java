package fr.alb.dto;

import fr.alb.billing.model.Contract;
import fr.alb.yard.model.EventConfig;

/**
 * Utility class for converting between Contract entities and DTOs.
 */
public class ContractMapper {

    /**
     * Convert a {@link Contract} to a {@link ContractDTO} enriched with the
     * provided {@link EventConfig} information.
     *
     * @param contract the contract to map
     * @param eventConfig the event configuration associated with the contract's
     *                    calculation mode, may be {@code null}
     * @return the mapped DTO
     */
    public static ContractDTO toDTO(Contract contract, EventConfig eventConfig) {
        ContractDTO dto = new ContractDTO();
        dto.id = contract.getId();
        dto.name = contract.name;
        dto.description = contract.description;

        if (contract.calculationMode != null) {
            CalculationModeDTO modeDto = new CalculationModeDTO();
            modeDto.type = contract.calculationMode.type;
            modeDto.subType = contract.calculationMode.subType;
            modeDto.eventConfig = EventMapper.toDTO(eventConfig);
            modeDto.filters = contract.calculationMode.filters;
            dto.calculationMode = modeDto;
        }

        dto.status = contract.status;
        dto.startDate = contract.startDate;
        dto.endDate = contract.endDate;
        dto.rates = contract.rates;
        dto.tariffId = contract.tariffId;
        dto.customerId = contract.customerId;
        dto.customerName = contract.customerName;
        dto.priority = contract.priority;
        dto.addendums = contract.addendums;
        return dto;
    }

    /**
     * Apply values from a {@link ContractUpdateDTO} to an existing
     * {@link Contract} entity.
     */
    public static void updateEntity(ContractUpdateDTO update, Contract contract) {
        if (update.name != null) {
            contract.name = update.name;
        }
        if (update.description != null) {
            contract.description = update.description;
        }
        if (update.calculationMode != null) {
            contract.calculationMode = update.calculationMode;
        }
        if (update.status != null) {
            contract.status = update.status;
        }
        if (update.startDate != null) {
            contract.startDate = update.startDate;
        }
        if (update.endDate != null) {
            contract.endDate = update.endDate;
        }
        if (update.rates != null) {
            contract.rates = update.rates;
        }
        if (update.tariffId != null) {
            contract.tariffId = update.tariffId;
        }
        if (update.customerId != null) {
            contract.customerId = update.customerId;
        }
        if (update.customerName != null) {
            contract.customerName = update.customerName;
        }
        if (update.priority != null) {
            contract.priority = update.priority;
        }
        if (update.addendums != null) {
            contract.addendums = update.addendums;
        }
    }
}

