package fr.alb.engine.contract;

import fr.alb.model.Event;
import fr.alb.yard.model.Item;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the applicable contract and rate for a given item+event combination.
 * Implementations must be CDI-injectable.
 */
public interface ContractResolver {

    /**
     * Returns the best matching contract+rate for the given item on the given date.
     * Returns empty if no active contract applies.
     *
     * @param item   the item being billed
     * @param event  the triggering event (IN or OUT)
     * @param date   the billing date (typically invoice creation date)
     */
    Optional<ContractMatch> resolve(Item item, Event event, LocalDate date);
}
