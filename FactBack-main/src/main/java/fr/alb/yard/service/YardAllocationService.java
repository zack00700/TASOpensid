package fr.alb.yard.service;

import fr.alb.platform.event.DomainEventPublisher;
import fr.alb.yard.event.ContainerMoved;
import fr.alb.yard.event.ItemArrived;
import fr.alb.yard.event.ItemDelivered;
import fr.alb.yard.model.ContainerMove;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.YardBlock;
import fr.alb.yard.model.YardSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Yard allocation + move orchestration (Kim, ch. 6-7).
 *
 * <p>Three primitives:
 * <ul>
 *     <li>{@link #allocateForArrival(String, String)} — find a compatible
 *         slot and place an item there (gate-in / discharge). Emits
 *         {@code yard.ItemArrived}.</li>
 *     <li>{@link #move(String, String, ContainerMove.MoveReason, String, String)}
 *         — re-position an item to another slot. Emits
 *         {@code yard.ContainerMoved}.</li>
 *     <li>{@link #release(String, String, String)} — free the slot when
 *         the item leaves the yard. Emits {@code yard.ItemDelivered}.</li>
 * </ul>
 *
 * <p>Placement strategy is intentionally simple: first free slot whose
 * block accepts the item's category, with reefer / OOG / weight guards.
 * Optimisation (Kim §6.4) lands later.
 */
@ApplicationScoped
public class YardAllocationService {

    private static final Logger LOG = Logger.getLogger(YardAllocationService.class);

    @Inject
    DomainEventPublisher domainEvents;

    /** Allocate a slot for a newly arriving item and record a GATE_IN move. */
    public YardSlot allocateForArrival(String itemId, String preferredBlockId) {
        Item item = Item.findById(itemId);
        if (item == null) throw new NotFoundException("Item " + itemId + " not found");

        YardSlot chosen = findBestSlot(item, preferredBlockId)
                .orElseThrow(() -> new BadRequestException(
                        "No compatible free yard slot available for item " + itemId));

        chosen.currentItemId = itemId;
        chosen.update();

        ContainerMove move = new ContainerMove();
        move.itemId = itemId;
        move.fromSlotId = null; // arrival from outside the yard
        move.toSlotId = String.valueOf(chosen.getId());
        move.reason = ContainerMove.MoveReason.GATE_IN;
        move.movedAt = Instant.now();
        move.persist();

        domainEvents.publish(new ItemArrived(
                itemId, String.valueOf(chosen.getId()), chosen.blockId, move.movedAt));

        LOG.infof("Allocated slot %s for item %s on block %s", chosen.code, itemId, chosen.blockId);
        return chosen;
    }

    /** Move an item from its current slot to another, or to/from outside the yard. */
    public ContainerMove move(String itemId,
                              String toSlotId,
                              ContainerMove.MoveReason reason,
                              String operator,
                              String notes) {
        if (itemId == null || reason == null) {
            throw new BadRequestException("itemId and reason are required");
        }

        // Resolve current slot (may be null if item was outside the yard).
        YardSlot currentSlot = findCurrentSlot(itemId);
        YardSlot destination = toSlotId != null ? YardSlot.findById(toSlotId) : null;

        if (toSlotId != null && destination == null) {
            throw new NotFoundException("Target slot " + toSlotId + " not found");
        }
        if (destination != null && !destination.active) {
            throw new BadRequestException("Target slot " + toSlotId + " is inactive");
        }
        if (destination != null && destination.currentItemId != null
                && !destination.currentItemId.equals(itemId)) {
            throw new BadRequestException("Target slot " + toSlotId + " is already occupied by "
                    + destination.currentItemId);
        }

        // Free the source.
        if (currentSlot != null && (destination == null
                || !currentSlot.getId().equals(destination.getId()))) {
            currentSlot.currentItemId = null;
            currentSlot.update();
        }

        // Occupy the destination.
        if (destination != null) {
            destination.currentItemId = itemId;
            destination.update();
        }

        ContainerMove move = new ContainerMove();
        move.itemId = itemId;
        move.fromSlotId = currentSlot != null ? String.valueOf(currentSlot.getId()) : null;
        move.toSlotId = destination != null ? String.valueOf(destination.getId()) : null;
        move.reason = reason;
        move.movedAt = Instant.now();
        move.operator = operator;
        move.notes = notes;
        move.persist();

        domainEvents.publish(new ContainerMoved(
                itemId, move.fromSlotId, move.toSlotId, reason, move.movedAt));
        return move;
    }

    /** Release the slot on final delivery / gate-out. Records a move with null destination. */
    public ContainerMove release(String itemId, String operator, String notes) {
        YardSlot currentSlot = findCurrentSlot(itemId);
        if (currentSlot == null) {
            // Already delivered or never in the yard — idempotent.
            LOG.infof("release(%s): item is not currently in any slot — ignoring.", itemId);
            return null;
        }
        String fromSlotId = String.valueOf(currentSlot.getId());
        currentSlot.currentItemId = null;
        currentSlot.update();

        ContainerMove move = new ContainerMove();
        move.itemId = itemId;
        move.fromSlotId = fromSlotId;
        move.toSlotId = null;
        move.reason = ContainerMove.MoveReason.GATE_OUT;
        move.movedAt = Instant.now();
        move.operator = operator;
        move.notes = notes;
        move.persist();

        domainEvents.publish(new ItemDelivered(itemId, fromSlotId, move.movedAt));
        return move;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<YardSlot> findBestSlot(Item item, String preferredBlockId) {
        // Restrict to free + active + compatible blocks.
        List<YardSlot> candidates;
        if (preferredBlockId != null) {
            candidates = YardSlot.list("blockId = ?1 and currentItemId is null and active = true",
                    preferredBlockId);
        } else {
            candidates = YardSlot.list("currentItemId is null and active = true");
        }

        for (YardSlot slot : candidates) {
            if (!isCompatible(slot, item)) continue;
            return Optional.of(slot);
        }
        return Optional.empty();
    }

    private boolean isCompatible(YardSlot slot, Item item) {
        boolean isReefer = item.isReeferFlag();
        boolean isHazmat = item.isHazmatFlag();
        boolean isOog    = item.isOogFlag();
        Double  weight   = item.getVerifiedWeight();

        if (isReefer && !slot.reeferReady) return false;
        if (isOog    && !slot.oogReady)    return false;
        if (slot.maxWeightKg != null && weight != null && weight > slot.maxWeightKg) return false;

        YardBlock block = YardBlock.findById(slot.blockId);
        if (block == null || !block.active) return false;
        if (isHazmat && block.kind != YardBlock.BlockKind.HAZMAT) return false;
        if (block.kind == YardBlock.BlockKind.HAZMAT && !isHazmat) return false;
        if (block.kind == YardBlock.BlockKind.REEFER && !isReefer) return false;
        return true;
    }

    private YardSlot findCurrentSlot(String itemId) {
        return YardSlot.find("currentItemId", itemId).firstResult();
    }
}
