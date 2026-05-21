package fr.alb.gate.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Audit log of one gate crossing — IN (entering the terminal) or OUT
 * (leaving). Written both when operations manually records the event and
 * when an RFID / LPR system feeds it.
 */
@MongoEntity(collection = "GATE_EVENT")
public class GateEvent extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Direction { IN, OUT }

    /** FK to {@link TruckAppointment#getId()}. Null for undeclared visits. */
    public String appointmentId;

    /** FK to {@link GatePass#getId()}. Null when no pass was issued. */
    public String gatePassId;

    public Direction direction;
    public Instant   occurredAt;
    public String    licensePlate;

    /** Which physical gate the truck used, e.g. "GATE-A". */
    public String gateCode;

    /** User or system that recorded the event. */
    public String recordedBy;
}
