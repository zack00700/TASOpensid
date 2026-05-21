package fr.alb.equipment.service;

import fr.alb.equipment.event.ShiftEnded;
import fr.alb.equipment.event.ShiftStarted;
import fr.alb.equipment.model.Operator;
import fr.alb.equipment.model.Shift;
import fr.alb.platform.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;

/**
 * Shift lifecycle: SCHEDULED → STARTED → ENDED, or CANCELLED before start.
 * Publishes {@code equipment.ShiftStarted} / {@code equipment.ShiftEnded}
 * for productivity analytics.
 */
@ApplicationScoped
public class ShiftService {

    @Inject
    DomainEventPublisher domainEvents;

    public Shift schedule(Shift in) {
        if (in == null || in.operatorId == null
                || in.scheduledStart == null || in.scheduledEnd == null) {
            throw new BadRequestException("operatorId, scheduledStart and scheduledEnd are required");
        }
        if (!in.scheduledEnd.isAfter(in.scheduledStart)) {
            throw new BadRequestException("scheduledEnd must be after scheduledStart");
        }
        Operator operator = Operator.findById(in.operatorId);
        if (operator == null) throw new NotFoundException("Operator " + in.operatorId + " not found");
        if (!operator.active) throw new BadRequestException("Operator " + in.operatorId + " is inactive");

        in.operatorName = operator.firstName + " " + operator.lastName;
        in.status = Shift.Status.SCHEDULED;
        in.persist();
        return in;
    }

    public Shift start(String shiftId, Instant actualStart) {
        Shift shift = load(shiftId);
        if (shift.status == Shift.Status.STARTED) return shift;
        if (shift.status != Shift.Status.SCHEDULED) {
            throw new BadRequestException("Cannot start shift in status " + shift.status);
        }
        shift.actualStart = actualStart != null ? actualStart : Instant.now();
        shift.status = Shift.Status.STARTED;
        shift.update();

        domainEvents.publish(new ShiftStarted(
                String.valueOf(shift.getId()), shift.operatorId,
                shift.craneId, shift.yardMachineId, shift.actualStart));
        return shift;
    }

    public Shift end(String shiftId, Instant actualEnd) {
        Shift shift = load(shiftId);
        if (shift.status == Shift.Status.ENDED) return shift;
        if (shift.status != Shift.Status.STARTED) {
            throw new BadRequestException("Cannot end shift in status " + shift.status);
        }
        shift.actualEnd = actualEnd != null ? actualEnd : Instant.now();
        shift.status = Shift.Status.ENDED;
        shift.update();

        domainEvents.publish(new ShiftEnded(
                String.valueOf(shift.getId()), shift.operatorId,
                shift.craneId, shift.yardMachineId,
                shift.actualStart, shift.actualEnd));
        return shift;
    }

    public Shift cancel(String shiftId, String reason) {
        Shift shift = load(shiftId);
        if (shift.status != Shift.Status.SCHEDULED) {
            throw new BadRequestException("Only SCHEDULED shifts can be cancelled (status=" + shift.status + ")");
        }
        shift.status = Shift.Status.CANCELLED;
        shift.notes = reason != null ? reason : shift.notes;
        shift.update();
        return shift;
    }

    private Shift load(String id) {
        Shift shift = Shift.findById(id);
        if (shift == null) throw new NotFoundException("Shift " + id + " not found");
        return shift;
    }
}
