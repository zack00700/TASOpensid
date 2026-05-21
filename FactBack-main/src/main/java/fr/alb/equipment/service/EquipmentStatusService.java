package fr.alb.equipment.service;

import fr.alb.equipment.event.EquipmentStatusChanged;
import fr.alb.equipment.model.Crane;
import fr.alb.equipment.model.YardMachine;
import fr.alb.platform.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;

/**
 * Centralised status transitions for equipment. Every state change is
 * recorded and published as {@code equipment.StatusChanged} so utilisation
 * and downtime KPIs can be derived without polling.
 */
@ApplicationScoped
public class EquipmentStatusService {

    @Inject
    DomainEventPublisher domainEvents;

    public Crane updateCraneStatus(String craneId, Crane.Status newStatus, String moveId) {
        Crane crane = Crane.findById(craneId);
        if (crane == null) throw new NotFoundException("Crane " + craneId + " not found");
        Crane.Status previous = crane.status;
        if (previous == newStatus) return crane;

        crane.status = newStatus;
        crane.currentMoveId = newStatus == Crane.Status.WORKING ? moveId : null;
        crane.update();

        domainEvents.publish(new EquipmentStatusChanged(
                String.valueOf(crane.getId()), "CRANE",
                previous != null ? previous.name() : null,
                newStatus.name(), Instant.now()));
        return crane;
    }

    public YardMachine updateMachineStatus(String machineId, YardMachine.Status newStatus, String moveId) {
        YardMachine machine = YardMachine.findById(machineId);
        if (machine == null) throw new NotFoundException("Yard machine " + machineId + " not found");
        YardMachine.Status previous = machine.status;
        if (previous == newStatus) return machine;

        machine.status = newStatus;
        machine.currentMoveId = newStatus == YardMachine.Status.WORKING ? moveId : null;
        machine.update();

        domainEvents.publish(new EquipmentStatusChanged(
                String.valueOf(machine.getId()), "YARD_MACHINE",
                previous != null ? previous.name() : null,
                newStatus.name(), Instant.now()));
        return machine;
    }
}
