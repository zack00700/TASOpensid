package fr.alb.equipment.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Instant;

/**
 * Fired when an operator clocks in for a shift.
 *
 * <p>Listeners: productivity KPIs (labour hours), payroll integration.
 */
public class ShiftStarted implements DomainEvent {

    public final String shiftId;
    public final String operatorId;
    public final String craneId;
    public final String yardMachineId;
    public final Instant startedAt;

    public ShiftStarted(String shiftId, String operatorId, String craneId,
                        String yardMachineId, Instant startedAt) {
        this.shiftId = shiftId;
        this.operatorId = operatorId;
        this.craneId = craneId;
        this.yardMachineId = yardMachineId;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
    }

    @Override public String eventType()   { return "equipment.ShiftStarted"; }
    @Override public String aggregateId() { return shiftId; }
    @Override public Instant occurredAt() { return startedAt; }
}
