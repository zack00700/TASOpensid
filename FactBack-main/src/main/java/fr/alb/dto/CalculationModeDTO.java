package fr.alb.dto;

import fr.alb.type.CalculationModeType;
import fr.alb.billing.model.CalcFilter;
import java.util.List;

/**
 * DTO used in API responses to expose calculation mode information
 * with the associated event configuration details.
 */
public class CalculationModeDTO {
    /**
     * Event configuration associated with this calculation mode.
     * Only public fields of the event are exposed through {@link EventDTO}.
     */
    public EventDTO eventConfig;
    public CalculationModeType type;
    public String subType;
    public List<CalcFilter> filters;
}

