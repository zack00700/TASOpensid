package fr.alb.customs.service;

import fr.alb.customs.event.DeclarationCleared;
import fr.alb.customs.event.DeclarationHeld;
import fr.alb.customs.event.DeclarationRejected;
import fr.alb.customs.event.DeclarationSubmitted;
import fr.alb.customs.model.CustomsDeclaration;
import fr.alb.platform.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle transitions for {@link CustomsDeclaration}. Every state change
 * is idempotent on re-entry (calling {@code clear()} on an already
 * CLEARED declaration is a no-op) and emits exactly one domain event.
 */
@ApplicationScoped
public class CustomsDeclarationService {

    @Inject
    DomainEventPublisher domainEvents;

    public CustomsDeclaration create(CustomsDeclaration in) {
        if (in == null || in.billOfLadingId == null || in.billOfLadingId.isBlank()) {
            throw new BadRequestException("billOfLadingId is required");
        }
        if (in.itemIds == null || in.itemIds.isEmpty()) {
            throw new BadRequestException("at least one itemId is required");
        }
        in.status = CustomsDeclaration.Status.DRAFT;
        in.persist();
        return in;
    }

    public CustomsDeclaration submit(String id) {
        CustomsDeclaration d = load(id);
        if (d.status == CustomsDeclaration.Status.SUBMITTED) return d;
        if (d.status != CustomsDeclaration.Status.DRAFT) {
            throw new BadRequestException("Only DRAFT declarations can be submitted (status=" + d.status + ")");
        }
        if (d.declarationReference == null || d.declarationReference.isBlank()) {
            // Fallback — in production the customs authority returns this number.
            d.declarationReference = "LOCAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        d.status = CustomsDeclaration.Status.SUBMITTED;
        d.submittedAt = Instant.now();
        d.update();

        domainEvents.publish(new DeclarationSubmitted(
                String.valueOf(d.getId()), d.billOfLadingId, d.itemIds,
                d.declarationReference, d.submittedAt));
        return d;
    }

    public CustomsDeclaration hold(String id, String reason) {
        CustomsDeclaration d = load(id);
        if (d.status == CustomsDeclaration.Status.HELD) return d;
        if (d.status != CustomsDeclaration.Status.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED declarations can be put on hold (status=" + d.status + ")");
        }
        d.status = CustomsDeclaration.Status.HELD;
        d.heldAt = Instant.now();
        d.holdReason = reason;
        d.update();

        domainEvents.publish(new DeclarationHeld(
                String.valueOf(d.getId()), d.billOfLadingId, reason, d.heldAt));
        return d;
    }

    public CustomsDeclaration clear(String id, BigDecimal assessedDuties) {
        CustomsDeclaration d = load(id);
        if (d.status == CustomsDeclaration.Status.CLEARED) return d;
        if (d.status != CustomsDeclaration.Status.SUBMITTED
                && d.status != CustomsDeclaration.Status.HELD) {
            throw new BadRequestException("Cannot clear declaration in status " + d.status);
        }
        d.status = CustomsDeclaration.Status.CLEARED;
        d.clearedAt = Instant.now();
        if (assessedDuties != null) d.assessedDuties = assessedDuties;
        d.update();

        domainEvents.publish(new DeclarationCleared(
                String.valueOf(d.getId()), d.billOfLadingId, d.itemIds,
                d.assessedDuties, d.clearedAt));
        return d;
    }

    public CustomsDeclaration reject(String id, String reason) {
        CustomsDeclaration d = load(id);
        if (d.status == CustomsDeclaration.Status.REJECTED) return d;
        if (d.status == CustomsDeclaration.Status.CLEARED) {
            throw new BadRequestException("Cannot reject an already CLEARED declaration");
        }
        d.status = CustomsDeclaration.Status.REJECTED;
        d.rejectedAt = Instant.now();
        d.rejectionReason = reason;
        d.update();

        domainEvents.publish(new DeclarationRejected(
                String.valueOf(d.getId()), d.billOfLadingId, reason, d.rejectedAt));
        return d;
    }

    private CustomsDeclaration load(String id) {
        CustomsDeclaration d = CustomsDeclaration.findById(id);
        if (d == null) throw new NotFoundException("Declaration " + id + " not found");
        return d;
    }
}
