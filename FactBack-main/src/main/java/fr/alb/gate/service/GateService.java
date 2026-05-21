package fr.alb.gate.service;

import fr.alb.customs.api.CustomsClearanceChecker;
import fr.alb.gate.event.GateIn;
import fr.alb.gate.event.GateOut;
import fr.alb.gate.event.GatePassIssued;
import fr.alb.gate.model.GateEvent;
import fr.alb.gate.model.GatePass;
import fr.alb.gate.model.TruckAppointment;
import fr.alb.platform.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Gate workflow: pre-announce → verify → issue pass → record IN → record OUT.
 *
 * <p>The service keeps the appointment status transitions consistent and
 * publishes the lifecycle events that other contexts need ({@code
 * gate.GateIn} triggers yard allocation or release on the listening side;
 * {@code gate.GatePassIssued} lets customers notify their drivers).
 */
@ApplicationScoped
public class GateService {

    private static final Logger LOG = Logger.getLogger(GateService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASS_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // avoid O/0, I/1

    /** How long a pass stays valid after the expected arrival. */
    private static final Duration DEFAULT_GRACE = Duration.ofHours(4);

    @Inject
    DomainEventPublisher domainEvents;

    /**
     * Customs gate-out guard. Injected through the context-public interface
     * so gate stays decoupled from the customs internals.
     */
    @Inject
    CustomsClearanceChecker customsCheck;

    // ── Appointment ─────────────────────────────────────────────────────────

    public TruckAppointment createAppointment(TruckAppointment in) {
        if (in == null || in.purpose == null || in.licensePlate == null
                || in.expectedArrival == null) {
            throw new BadRequestException("purpose, licensePlate and expectedArrival are required");
        }
        in.status = TruckAppointment.Status.PENDING;
        in.persist();
        return in;
    }

    public TruckAppointment rejectAppointment(String appointmentId, String reason) {
        TruckAppointment ap = loadAppointment(appointmentId);
        if (ap.status != TruckAppointment.Status.PENDING) {
            throw new BadRequestException("Only PENDING appointments can be rejected (status=" + ap.status + ")");
        }
        ap.status = TruckAppointment.Status.REJECTED;
        ap.notes = reason != null ? reason : ap.notes;
        ap.update();
        return ap;
    }

    public TruckAppointment cancelAppointment(String appointmentId, String reason) {
        TruckAppointment ap = loadAppointment(appointmentId);
        if (ap.status == TruckAppointment.Status.COMPLETED
                || ap.status == TruckAppointment.Status.ARRIVED) {
            throw new BadRequestException("Cannot cancel an appointment once the truck has arrived");
        }
        ap.status = TruckAppointment.Status.CANCELLED;
        ap.notes = reason != null ? reason : ap.notes;
        ap.update();
        return ap;
    }

    // ── Gate pass ───────────────────────────────────────────────────────────

    public GatePass issuePass(String appointmentId, String issuedBy) {
        TruckAppointment ap = loadAppointment(appointmentId);
        if (ap.status != TruckAppointment.Status.PENDING) {
            throw new BadRequestException("Only PENDING appointments can receive a pass (status=" + ap.status + ")");
        }

        GatePass pass = new GatePass();
        pass.appointmentId = appointmentId;
        pass.code = generatePassCode();
        pass.issuedAt = Instant.now();
        Instant window = ap.expectedWindowEnd != null ? ap.expectedWindowEnd : ap.expectedArrival;
        pass.expiresAt = window.plus(DEFAULT_GRACE);
        pass.status = GatePass.Status.ACTIVE;
        pass.issuedBy = issuedBy;
        pass.persist();

        ap.status = TruckAppointment.Status.APPROVED;
        ap.update();

        domainEvents.publish(new GatePassIssued(
                String.valueOf(pass.getId()), appointmentId, pass.code, pass.issuedAt));
        LOG.infof("Issued gate pass %s for appointment %s (expires %s)",
                pass.code, appointmentId, pass.expiresAt);
        return pass;
    }

    public GatePass revokePass(String gatePassId, String reason) {
        GatePass pass = GatePass.findById(gatePassId);
        if (pass == null) throw new NotFoundException("Gate pass " + gatePassId + " not found");
        if (pass.status != GatePass.Status.ACTIVE) {
            throw new BadRequestException("Only ACTIVE passes can be revoked (status=" + pass.status + ")");
        }
        pass.status = GatePass.Status.REVOKED;
        pass.update();
        return pass;
    }

    // ── Gate events ─────────────────────────────────────────────────────────

    public GateEvent recordGateIn(String gatePassCode, String gateCode, String recordedBy) {
        GatePass pass = findActivePassByCode(gatePassCode);
        TruckAppointment ap = loadAppointment(pass.appointmentId);
        if (ap.status != TruckAppointment.Status.APPROVED) {
            throw new BadRequestException("Appointment " + ap.getId() + " is not APPROVED (status=" + ap.status + ")");
        }

        GateEvent evt = new GateEvent();
        evt.appointmentId = pass.appointmentId;
        evt.gatePassId = String.valueOf(pass.getId());
        evt.direction = GateEvent.Direction.IN;
        evt.occurredAt = Instant.now();
        evt.licensePlate = ap.licensePlate;
        evt.gateCode = gateCode;
        evt.recordedBy = recordedBy;
        evt.persist();

        pass.status = GatePass.Status.USED;
        pass.update();

        ap.status = TruckAppointment.Status.ARRIVED;
        ap.update();

        domainEvents.publish(new GateIn(
                String.valueOf(evt.getId()),
                ap.getId() != null ? ap.getId().toString() : null,
                ap.itemId,
                ap.billOfLadingId,
                ap.licensePlate,
                evt.occurredAt));
        return evt;
    }

    public GateEvent recordGateOut(String appointmentId, String gateCode, String recordedBy) {
        TruckAppointment ap = loadAppointment(appointmentId);
        if (ap.status != TruckAppointment.Status.ARRIVED) {
            throw new BadRequestException("Appointment " + appointmentId + " is not ARRIVED (status=" + ap.status + ")");
        }
        if (ap.itemId != null && !ap.itemId.isBlank() && !customsCheck.isCleared(ap.itemId)) {
            String reason = customsCheck.lastBlockReason(ap.itemId);
            throw new BadRequestException("Gate-out blocked by customs: "
                    + (reason != null ? reason : "item not cleared."));
        }

        GateEvent evt = new GateEvent();
        evt.appointmentId = appointmentId;
        evt.direction = GateEvent.Direction.OUT;
        evt.occurredAt = Instant.now();
        evt.licensePlate = ap.licensePlate;
        evt.gateCode = gateCode;
        evt.recordedBy = recordedBy;
        evt.persist();

        ap.status = TruckAppointment.Status.COMPLETED;
        ap.update();

        domainEvents.publish(new GateOut(
                String.valueOf(evt.getId()),
                appointmentId,
                ap.itemId,
                ap.licensePlate,
                evt.occurredAt));
        return evt;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private TruckAppointment loadAppointment(String id) {
        TruckAppointment ap = TruckAppointment.findById(id);
        if (ap == null) throw new NotFoundException("Appointment " + id + " not found");
        return ap;
    }

    private GatePass findActivePassByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("gate pass code is required");
        }
        GatePass pass = GatePass.find("code", code).firstResult();
        if (pass == null) throw new NotFoundException("No gate pass with code " + code);
        if (pass.status != GatePass.Status.ACTIVE) {
            throw new BadRequestException("Gate pass " + code + " is not active (status=" + pass.status + ")");
        }
        if (pass.expiresAt != null && Instant.now().isAfter(pass.expiresAt)) {
            pass.status = GatePass.Status.EXPIRED;
            pass.update();
            throw new BadRequestException("Gate pass " + code + " is expired");
        }
        return pass;
    }

    private static String generatePassCode() {
        char[] buf = new char[10];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = PASS_ALPHABET.charAt(RANDOM.nextInt(PASS_ALPHABET.length()));
        }
        return new String(buf);
    }
}
