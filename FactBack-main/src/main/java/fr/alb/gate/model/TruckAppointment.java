package fr.alb.gate.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

/**
 * Pre-announced truck visit — shipper or trucking company declares when a
 * truck will arrive and what it is coming to pick up or drop off.
 *
 * <p>The appointment is the first step of a gate workflow: pre-announce →
 * verify documents → issue {@link GatePass} → truck arrives (GATE_IN) →
 * truck leaves (GATE_OUT).
 */
@MongoEntity(collection = "TRUCK_APPOINTMENT")
public class TruckAppointment extends EntityBase {

    public static final long serialVersionUID = 1L;

    public enum Purpose { PICKUP, DROPOFF }

    public enum Status {
        PENDING,    // submitted, waiting for verification
        APPROVED,   // documents OK, GatePass issued
        REJECTED,   // documents not OK
        ARRIVED,    // truck crossed the gate IN
        COMPLETED,  // truck crossed the gate OUT
        CANCELLED,
        NO_SHOW     // truck did not arrive within the grace period
    }

    public Purpose purpose;

    /** Expected arrival window start. */
    public Instant expectedArrival;
    public Instant expectedWindowEnd;

    /** Truck identity. */
    public String licensePlate;
    public String driverName;
    public String driverIdDocument;
    public String truckingCompany;

    /** What the truck is coming for. Exactly one of these is typically set. */
    public String billOfLadingId;     // for pickup of items referenced by a BOL
    public String itemId;             // when a specific container is announced
    public String bookingNumber;      // for dropoff of an export container

    public Status status = Status.PENDING;

    /** Free-form note from the customer or the gate agent. */
    public String notes;
}
