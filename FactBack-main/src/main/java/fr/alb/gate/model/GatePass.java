package fr.alb.gate.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Access token issued to an approved {@link TruckAppointment}.
 *
 * <p>The truck presents the {@code code} (QR / barcode) at the gate; the
 * gate agent scans it and records a {@link GateEvent}. Passes are single-
 * use and tied to a specific appointment.
 */
@MongoEntity(collection = "GATE_PASS")
public class GatePass extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Status { ACTIVE, USED, REVOKED, EXPIRED }

    /** FK to {@link TruckAppointment#getId()}. */
    public String appointmentId;

    /** The token scanned at the gate. Typically a short alphanumeric string. */
    public String code;

    /** Validity window. {@code expiresAt} is usually {@code expectedArrival + grace}. */
    public Instant issuedAt;
    public Instant expiresAt;

    public Status status = Status.ACTIVE;

    /** Ops staff that issued the pass. */
    public String issuedBy;
}
