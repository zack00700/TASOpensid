package fr.alb.customs.service;

import fr.alb.customs.api.CustomsClearanceChecker;
import fr.alb.customs.model.CustomsDeclaration;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Mongo-backed implementation of
 * {@link fr.alb.customs.api.CustomsClearanceChecker}. The gate context
 * depends only on the interface.
 */
@ApplicationScoped
public class DefaultCustomsClearanceChecker implements CustomsClearanceChecker {

    @Override
    public boolean isCleared(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        List<CustomsDeclaration> hits = CustomsDeclaration.list(
                "status = ?1 and itemIds = ?2",
                CustomsDeclaration.Status.CLEARED, itemId);
        return !hits.isEmpty();
    }

    @Override
    public String lastBlockReason(String itemId) {
        if (itemId == null || itemId.isBlank()) return "missing itemId";
        List<CustomsDeclaration> any = CustomsDeclaration.list("itemIds", itemId);
        if (any.isEmpty()) return "No customs declaration covers this item.";
        CustomsDeclaration worst = any.get(0);
        for (CustomsDeclaration d : any) {
            if (d.status == CustomsDeclaration.Status.CLEARED) return null;
            if (d.status.ordinal() < worst.status.ordinal()) worst = d;
        }
        return switch (worst.status) {
            case DRAFT     -> "Declaration is still in DRAFT — submit it first.";
            case SUBMITTED -> "Declaration is SUBMITTED but customs has not cleared yet.";
            case HELD      -> "Declaration is HELD: " + (worst.holdReason != null ? worst.holdReason : "awaiting inspection.");
            case REJECTED  -> "Declaration was REJECTED: " + (worst.rejectionReason != null ? worst.rejectionReason : "create a new declaration.");
            case CLEARED   -> null;
        };
    }
}
