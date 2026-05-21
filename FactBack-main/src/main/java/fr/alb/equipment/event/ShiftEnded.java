package fr.alb.equipment.event;

import fr.alb.platform.event.DomainEvent;

import java.time.Duration;
import java.time.Instant;

/**
 * Fired when an operator clocks out. Carries the elapsed duration for
 * immediate consumption by productivity dashboards.
 */
public class ShiftEnded implements DomainEvent {

    public final String shiftId;
    public final String operatorId;
    public final String craneId;
    public final String yardMachineId;
    public final Instant endedAt;
    public final Duration duration;

    public ShiftEnded(String shiftId, String operatorId, String craneId,
                      String yardMachineId, Instant startedAt, Instant endedAt) {
        this.shiftId = shiftId;
        this.operatorId = operatorId;
        this.craneId = craneId;
        this.yardMachineId = yardMachineId;
        this.endedAt = endedAt != null ? endedAt : Instant.now();
        this.duration = startedAt != null ? Duration.between(startedAt, this.endedAt) : Duration.ZERO;
    }

    @Override public String eventType()   { return "equipment.ShiftEnded"; }
    @Override public String aggregateId() { return shiftId; }
    @Override public Instant occurredAt() { return endedAt; }
}
