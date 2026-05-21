package fr.alb.billing.model;

import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import fr.alb.yard.model.EventConfig;
import java.util.List;

import fr.alb.billing.model.CalcFilter;

/**
 * Part of a {@link Contract} describing how the contract should be calculated.
 * <p>
 * The {@code eventConfig} field holds a reference to the
 * {@link fr.alb.model.EventConfig} used to compute the contract. Only the
 * identifier is expected when the contract is created or updated, while the
 * full event details can be fetched on demand.
 */
public class CalculationMode {
        /** Reference to the event configuration used by this calculation mode. */
        public EventConfig eventConfig;

        public CalculationModeType type;
        public String subType;
        /** Optional list of filters applied when selecting items. */
        public List<CalcFilter> filters;

        public CalculationMode() {
                super();
        }

        /**
         * Returns the {@code subType} string as a typed {@link CalculationSubType} enum.
         * Legacy MongoDB documents storing e.g. "in_date" are resolved transparently.
         * Use this in new code instead of comparing {@code subType} strings directly.
         */
        public CalculationSubType subTypeEnum() {
                return CalculationSubType.from(this.subType);
        }

        @Override
        public String toString() {
                return "CalculationMode [eventConfig=" + (eventConfig != null ? eventConfig.getId() : null)
                                + ", type=" + type + ", subType=" + subType + "]";
        }
}
