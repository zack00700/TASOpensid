package fr.alb.billing.service;

import fr.alb.bol.model.BillOfLading;
import fr.alb.billing.model.Contract;
import fr.alb.yard.model.Commodity;
import fr.alb.model.Event;
import fr.alb.yard.model.EventConfig;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.domain.BillingContext;
import fr.alb.billing.domain.ChargeResult;
import fr.alb.engine.calculation.ChargeCalculatorRegistry;
import fr.alb.type.CalculationModeType;
import fr.alb.type.CalculationSubType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for invoice-line calculations: resolving item events,
 * identifying IN/OUT dates, and computing billable amounts for each contract
 * calculation mode.
 *
 * <p>Extracted from {@link fr.alb.dao.InvoiceDaoImpl} — no business logic was
 * changed, only the location of the code.</p>
 */
@ApplicationScoped
public class InvoiceCalculationService {

    private static final Logger LOGGER = Logger.getLogger(InvoiceCalculationService.class);

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    @Inject
    RateSelectionService rateSelectionService;

    @Inject
    ChargeCalculatorRegistry calculatorRegistry;

    private ZoneId zone() {
        return ZoneId.of(timezone);
    }

    // -------------------------------------------------------------------------
    // Event resolution
    // -------------------------------------------------------------------------

    /**
     * Returns all {@link Event}s associated with the given item, sorted
     * chronologically.  Resolves lifecycle and event-config data in bulk to
     * avoid N+1 queries.
     *
     * @param item the item whose events are to be loaded
     * @return sorted list of events (may be empty, never {@code null})
     */
    public List<Event> getItemEvents(Item item) {
        List<Event> events = new ArrayList<>();
        List<Lifecycle> lcs;
        if (item.getLifeCycles() != null && !item.getLifeCycles().isEmpty()) {
            lcs = Lifecycle.find("_id in ?1", item.getLifeCycles()).list();
        } else {
            lcs = Lifecycle.find("itemId", item.getId()).list();
        }

        // Collect all event IDs first to avoid N+1 queries
        Set<String> allEventIds = lcs.stream()
                .flatMap(lc -> lc.getEventIds().stream())
                .collect(Collectors.toSet());

        if (allEventIds.isEmpty()) {
            return events;
        }

        // Bulk fetch all ItemEvents in one query
        List<ItemEvent> itemEvents = ItemEvent.find("_id in ?1", allEventIds).list();

        // Create a map for O(1) lookup
        Map<String, ItemEvent> eventMap = itemEvents.stream()
                .collect(Collectors.toMap(ItemEvent::getId, evt -> evt));

        // Collect all event config IDs
        Set<String> allEventConfigIds = itemEvents.stream()
                .map(ItemEvent::getEventId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Bulk fetch all EventConfigs in one query
        Map<String, EventConfig> configMap = new HashMap<>();
        if (!allEventConfigIds.isEmpty()) {
            List<EventConfig> eventConfigs = EventConfig.find("_id in ?1", allEventConfigIds).list();
            configMap = eventConfigs.stream()
                    .collect(Collectors.toMap(EventConfig::getId, cfg -> cfg));
        }

        // Build events using the maps
        for (Lifecycle lc : lcs) {
            for (String eid : lc.getEventIds()) {
                ItemEvent evt = eventMap.get(eid);
                if (evt != null) {
                    EventConfig cfg = configMap.get(evt.getEventId());
                    if (cfg != null && evt.getEventDate() != null) {
                        Event e = new Event();
                        e.setType(cfg.getEventType().name());
                        e.setTimeStamp(Date.from(evt.getEventDate().atZone(zone()).toInstant()));
                        events.add(e);
                    } else if (cfg == null) {
                        LOGGER.warnf("EventConfig not found for eventId=%s, skipping", evt.getEventId());
                    } else {
                        LOGGER.warnf("ItemEvent %s has null eventDate, skipping", evt.getId());
                    }
                }
            }
        }
        events.sort(Comparator.comparing(Event::getTimeStamp));
        return events;
    }

    /**
     * Bulk version of {@link #getItemEvents(Item)}.
     * Fetches Lifecycles, ItemEvents, and EventConfigs for ALL items in 3 queries total,
     * regardless of how many items are in the list.
     *
     * @param items the items to resolve events for
     * @return map of itemId → sorted list of events (never {@code null})
     */
    public Map<String, List<Event>> getItemEventsMap(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return new HashMap<>();
        }

        // Step 1: collect all lifecycle IDs across all items, grouped by itemId
        // Items that carry explicit lifeCycle IDs are resolved directly;
        // items without them are queried by itemId in a single IN query.
        Set<String> explicitLcIds = new HashSet<>();
        Set<String> itemIdsNeedingLcLookup = new HashSet<>();
        Map<String, String> lcIdToItemId = new HashMap<>();  // lifecycle ID → item ID

        for (Item item : items) {
            if (item.getLifeCycles() != null && !item.getLifeCycles().isEmpty()) {
                for (String lcId : item.getLifeCycles()) {
                    explicitLcIds.add(lcId);
                    lcIdToItemId.put(lcId, item.getId());
                }
            } else {
                itemIdsNeedingLcLookup.add(item.getId());
            }
        }

        // Step 2: bulk fetch lifecycles (at most 2 queries)
        List<Lifecycle> allLifecycles = new ArrayList<>();
        if (!explicitLcIds.isEmpty()) {
            allLifecycles.addAll(Lifecycle.find("_id in ?1", explicitLcIds).list());
        }
        if (!itemIdsNeedingLcLookup.isEmpty()) {
            List<Lifecycle> byItemId = Lifecycle.find("itemId in ?1", itemIdsNeedingLcLookup).list();
            for (Lifecycle lc : byItemId) {
                lcIdToItemId.put(lc.getId(), lc.getItemId());
            }
            allLifecycles.addAll(byItemId);
        }

        // Step 3: collect all event IDs
        Set<String> allEventIds = allLifecycles.stream()
                .flatMap(lc -> lc.getEventIds().stream())
                .collect(Collectors.toSet());

        if (allEventIds.isEmpty()) {
            Map<String, List<Event>> empty = new HashMap<>();
            items.forEach(i -> empty.put(i.getId(), new ArrayList<>()));
            return empty;
        }

        // Step 4: bulk fetch all ItemEvents (1 query)
        List<ItemEvent> itemEventList = ItemEvent.find("_id in ?1", allEventIds).list();
        Map<String, ItemEvent> eventMap = new HashMap<>();
        for (ItemEvent ie : itemEventList) {
            eventMap.put(ie.getId(), ie);
        }

        // Step 5: bulk fetch all EventConfigs (1 query)
        Set<String> configIds = new HashSet<>();
        for (ItemEvent ie : itemEventList) {
            if (ie.getEventId() != null) configIds.add(ie.getEventId());
        }
        Map<String, EventConfig> configMap = new HashMap<>();
        if (!configIds.isEmpty()) {
            List<EventConfig> cfgList = EventConfig.find("_id in ?1", configIds).list();
            for (EventConfig cfg : cfgList) {
                configMap.put(cfg.getId(), cfg);
            }
        }

        // Step 6: build result map
        Map<String, List<Event>> result = new HashMap<>();
        items.forEach(i -> result.put(i.getId(), new ArrayList<>()));

        for (Lifecycle lc : allLifecycles) {
            String itemId = lcIdToItemId.get(lc.getId());
            if (itemId == null) continue;
            List<Event> itemEvents = result.computeIfAbsent(itemId, k -> new ArrayList<>());

            for (String eid : lc.getEventIds()) {
                ItemEvent evt = eventMap.get(eid);
                if (evt == null) continue;
                EventConfig cfg = configMap.get(evt.getEventId());
                if (cfg != null && evt.getEventDate() != null) {
                    Event e = new Event();
                    e.setType(cfg.getEventType().name());
                    e.setTimeStamp(Date.from(evt.getEventDate().atZone(zone()).toInstant()));
                    itemEvents.add(e);
                }
            }
        }

        result.values().forEach(list -> list.sort(Comparator.comparing(Event::getTimeStamp)));
        return result;
    }

    /**
     * Returns the single IN event for an item, or {@code null} if none exists.
     * Throws {@link IllegalStateException} if more than one IN event is found.
     *
     * @param item the item to inspect
     * @return the single IN event, or {@code null}
     * @throws IllegalStateException if multiple IN events are found
     */
    public Event findSingleInEvent(Item item) {
        Event found = null;
        for (Event e : getItemEvents(item)) {
            if ("IN".equals(e.getType())) {
                if (found != null) {
                    throw new IllegalStateException("Multiple IN events found for item " + item.getId());
                }
                found = e;
            }
        }
        if (LOGGER.isDebugEnabled()) {
            if (found != null) {
                LOGGER.debugf("[InvoiceCalc] Found IN event for Item %s at %s", item.getId(), found.getTimeStamp());
            } else {
                LOGGER.debugf("[InvoiceCalc] No IN event found for Item %s", item.getId());
            }
        }
        return found;
    }

    // -------------------------------------------------------------------------
    // Amount calculation
    // -------------------------------------------------------------------------

    /**
     * Calculates the billable amount for one item/event/contract combination.
     * Dispatches to the appropriate sub-calculation based on the contract's
     * {@link fr.alb.type.CalculationModeType}.
     *
     * @param contract the contract to apply
     * @param item     the item being billed
     * @param event    the triggering event
     * @return computed amount (never {@code null}; returns {@link BigDecimal#ZERO}
     *         when inapplicable)
     */
    /**
     * @deprecated Use {@link #calculateCharge(Contract, Item, Event)} which returns a full ChargeResult with audit trail.
     */
    @Deprecated
    public BigDecimal calculateAmount(Contract contract, Item item, Event event) {
        return calculateCharge(contract, item, event).amount();
    }

    /**
     * Calculates the charge for the given item/event using the contract's calculation mode.
     * Returns a {@link ChargeResult} with full audit trail.
     *
     * <p>The event type determines which of inEvent/outEvent is populated in the context.
     * Delegates to {@link #calculateCharge(Contract, Item, Event, Event, LocalDate, BillOfLading)}
     * using {@link LocalDate#now()} as the invoice date and no BillOfLading.
     */
    public ChargeResult calculateCharge(Contract contract, Item item, Event event) {
        Event inEvent  = "IN".equalsIgnoreCase(event.getType())  ? event : null;
        Event outEvent = "OUT".equalsIgnoreCase(event.getType()) ? event : null;
        return calculateCharge(contract, item, inEvent, outEvent, LocalDate.now(), null);
    }

    /**
     * Full-parameter variant of charge calculation.  All context is supplied explicitly,
     * so rate selection and the {@link BillingContext} use the correct invoice date rather
     * than today.
     *
     * @param inEvent     the IN event for this item, or {@code null}
     * @param outEvent    the OUT event for this item, or {@code null}
     * @param invoiceDate the reference date for rate selection and day calculations
     * @param billOfLading the associated BillOfLading, or {@code null}
     */
    public ChargeResult calculateCharge(Contract contract, Item item,
                                        Event inEvent, Event outEvent,
                                        LocalDate invoiceDate,
                                        BillOfLading billOfLading) {
        if (contract == null || contract.calculationMode == null) {
            return ChargeResult.zero("EUR");
        }
        if (contract.rates == null || contract.rates.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[InvoiceCalc] Contract %s has no rates - skipping item %s",
                        contract.getId(), item != null ? item.getId() : "null");
            }
            return ChargeResult.zero("EUR");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[InvoiceCalc] Contract=%s mode=%s/%s Item=%s invoiceDate=%s",
                    contract.getId(), contract.calculationMode.type,
                    contract.calculationMode.subType,
                    item != null ? item.getId() : "null", invoiceDate);
        }

        CalculationSubType subType = CalculationSubType.from(contract.calculationMode.subType);

        // Select rate using the correct invoice date and item filters
        RateManagement selectedRate = rateSelectionService.selectRate(
            contract.rates,
            invoiceDate,
            null,
            contract.calculationMode.type == CalculationModeType.DATE_BY_TEU ? "DAY" : null,
            item != null && item.getCategory() != null ? item.getCategory().getValue() : null,
            item != null && item.getFreightKind() != null ? item.getFreightKind().getValue() : null
        );
        if (selectedRate == null && !contract.rates.isEmpty()) {
            selectedRate = contract.rates.get(0); // fallback to first rate
        }

        BillingContext ctx = new BillingContext(
            item,
            contract,
            selectedRate,
            inEvent,
            outEvent,
            invoiceDate,
            billOfLading
        );

        return calculatorRegistry.resolve(contract.calculationMode.type, subType).calculate(ctx);
    }

    /**
     * Calculates a date-based amount from the IN event date to today.
     *
     * @param contract the contract supplying the rate
     * @param inEvent  the IN event
     * @return computed amount
     */
    public BigDecimal calculateAmountByDate(Contract contract, Event inEvent) {
        if (contract == null || contract.calculationMode == null || inEvent == null
                || contract.rates == null || contract.rates.isEmpty() || !"IN".equals(inEvent.getType())) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[InvoiceCalc] Invalid data for date calculation - returning 0");
            }
            return BigDecimal.ZERO;
        }

        LocalDate inDate = inEvent.getTimeStamp().toInstant().atZone(zone()).toLocalDate();
        LocalDate today = LocalDate.now(zone());

        long days = ChronoUnit.DAYS.between(inDate, today);
        if (days < 0) {
            days = 0;
        }

        RateManagement rate = contract.rates.get(0);
        BigDecimal total = BigDecimal.valueOf(rate.getAmount()).multiply(BigDecimal.valueOf(days));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf(
                    "[InvoiceCalc] Contract=%s, Event=IN (%s), InvoiceDate=%s → Days=%d, Rate=%.2f %s/%s → Total=%.2f %s",
                    contract.getId(), inDate, today, days, rate.getAmount(), rate.getCurrency(),
                    rate.getUnitOfMeasurement(), total.doubleValue(), rate.getCurrency());
        }

        return total;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Date findOutDateForInEvent(Item item, Event inEvent) {
        for (Event event : getItemEvents(item)) {
            if ("OUT".equals(event.getType())
                    && event.getTimeStamp().after(inEvent.getTimeStamp())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("[InvoiceCalc] Found OUT event for Item %s at %s", item.getId(), event.getTimeStamp());
                }
                return event.getTimeStamp();
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[InvoiceCalc] No OUT event found for Item %s after IN %s", item.getId(),
                    inEvent.getTimeStamp());
        }
        return null;
    }

    private BigDecimal calculateByBLVolume(Contract contract, Item item) {
        BillOfLading bol = getBillOfLadingForItem(item);
        if (bol == null || bol.getCommodity() == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[InvoiceCalc] BL_VOLUME: No BillOfLading or commodity for item %s", item.getId());
            }
            return BigDecimal.ZERO;
        }

        Double volumeM3 = bol.getCommodity().getVolumeM3();
        if (volumeM3 == null || volumeM3 <= 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[InvoiceCalc] BL_VOLUME: No volume data for item %s", item.getId());
            }
            return BigDecimal.ZERO;
        }

        RateManagement rate = contract.rates.get(0);
        BigDecimal volume = BigDecimal.valueOf(volumeM3);
        BigDecimal total = BigDecimal.valueOf(rate.getAmount()).multiply(volume);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[InvoiceCalc] BL_VOLUME: Item=%s, Volume=%.3f m³, Rate=%.2f %s/m³, Total=%.2f %s",
                    item.getId(), volumeM3, rate.getAmount(), rate.getCurrency(),
                    total.doubleValue(), rate.getCurrency());
        }

        return total;
    }

    private BigDecimal calculateByBLWeight(Contract contract, Item item) {
        BillOfLading bol = getBillOfLadingForItem(item);
        if (bol == null || bol.getCommodity() == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[InvoiceCalc] BL_WEIGHT: No BillOfLading or commodity for item %s", item.getId());
            }
            return BigDecimal.ZERO;
        }

        Double weightKg = bol.getCommodity().getWeightKg();
        if (weightKg == null || weightKg <= 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[InvoiceCalc] BL_WEIGHT: No weight data for item %s", item.getId());
            }
            return BigDecimal.ZERO;
        }

        RateManagement rate = contract.rates.get(0);
        BigDecimal weight = BigDecimal.valueOf(weightKg);
        BigDecimal total = BigDecimal.valueOf(rate.getAmount()).multiply(weight);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[InvoiceCalc] BL_WEIGHT: Item=%s, Weight=%.3f kg, Rate=%.2f %s/kg, Total=%.2f %s",
                    item.getId(), weightKg, rate.getAmount(), rate.getCurrency(),
                    total.doubleValue(), rate.getCurrency());
        }

        return total;
    }

    private BillOfLading getBillOfLadingForItem(Item item) {
        if (item.getBillOfLadingId() == null) {
            return null;
        }
        return BillOfLading.findById(item.getBillOfLadingId());
    }

    // -------------------------------------------------------------------------
    // Tiered / Banded helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the billable quantity for TIERED/BANDED modes based on the
     * contract's subType:
     * <ul>
     *   <li>{@code "date"} / {@code "in_date"} — days from IN event to today</li>
     *   <li>{@code "date_by_teu"} — days from IN event to OUT event</li>
     *   <li>{@code "bl_volume"} — volume (m³) from the Bill of Lading</li>
     *   <li>{@code "bl_weight"} — weight (kg) from the Bill of Lading</li>
     *   <li>anything else — fixed quantity of 1</li>
     * </ul>
     */
    private BigDecimal resolveQuantity(Contract contract, Item item, Event inEvent) {
        CalculationSubType subType = CalculationSubType.from(contract.calculationMode.subType);

        if (subType == CalculationSubType.IN_DATE) {
            LocalDate inDate = inEvent.getTimeStamp().toInstant().atZone(zone()).toLocalDate();
            long days = Math.max(0, ChronoUnit.DAYS.between(inDate, LocalDate.now(zone())));
            return BigDecimal.valueOf(days);
        } else if (subType == CalculationSubType.DATE_BY_TEU) {
            Date outDate = findOutDateForInEvent(item, inEvent);
            if (outDate == null) return BigDecimal.ZERO;
            long days = ChronoUnit.DAYS.between(
                    inEvent.getTimeStamp().toInstant().atZone(zone()).toLocalDate(),
                    outDate.toInstant().atZone(zone()).toLocalDate());
            return BigDecimal.valueOf(Math.max(0, days));
        } else if (subType == CalculationSubType.BL_VOLUME) {
            BillOfLading bol = getBillOfLadingForItem(item);
            if (bol == null || bol.getCommodity() == null) return BigDecimal.ZERO;
            Double vol = bol.getCommodity().getVolumeM3();
            return (vol != null && vol > 0) ? BigDecimal.valueOf(vol) : BigDecimal.ZERO;
        } else if (subType == CalculationSubType.BL_WEIGHT) {
            BillOfLading bol = getBillOfLadingForItem(item);
            if (bol == null || bol.getCommodity() == null) return BigDecimal.ZERO;
            Double wt = bol.getCommodity().getWeightKg();
            return (wt != null && wt > 0) ? BigDecimal.valueOf(wt) : BigDecimal.ZERO;
        } else {
            return BigDecimal.ONE;
        }
    }

    /**
     * Tiered calculation: sums the cost for <em>every tier traversed</em> up to
     * {@code totalQty}.  Each tier contributes:
     * {@code flatCost + (quantityInTier × unitCost)}.
     */
    BigDecimal calculateTiered(List<RateManagement> rates, BigDecimal totalQty) {
        List<RateManagement> sorted = rates.stream()
                .sorted(Comparator.comparingDouble(RateManagement::getStartQuantity))
                .collect(Collectors.toList());

        BigDecimal total = BigDecimal.ZERO;
        for (RateManagement tier : sorted) {
            BigDecimal tierStart = BigDecimal.valueOf(tier.getStartQuantity());
            BigDecimal tierEnd = BigDecimal.valueOf(tier.getEndQuantity());

            if (totalQty.compareTo(tierStart) <= 0) break;

            BigDecimal qtyInTier = totalQty.min(tierEnd).subtract(tierStart);
            BigDecimal tierAmount = BigDecimal.valueOf(tier.getFlatCost())
                    .add(qtyInTier.multiply(BigDecimal.valueOf(tier.getAmount())));
            total = total.add(tierAmount);
        }
        return total;
    }

    /**
     * Banded calculation: applies the rate of the <em>single band</em> where
     * {@code totalQty} falls.  Amount = {@code flatCost + (totalQty × unitCost)}.
     */
    BigDecimal calculateBanded(List<RateManagement> rates, BigDecimal totalQty) {
        List<RateManagement> sorted = rates.stream()
                .sorted(Comparator.comparingDouble(RateManagement::getStartQuantity))
                .collect(Collectors.toList());

        for (RateManagement band : sorted) {
            BigDecimal bandStart = BigDecimal.valueOf(band.getStartQuantity());
            BigDecimal bandEnd = BigDecimal.valueOf(band.getEndQuantity());

            if (totalQty.compareTo(bandStart) >= 0 && totalQty.compareTo(bandEnd) < 0) {
                return BigDecimal.valueOf(band.getFlatCost())
                        .add(totalQty.multiply(BigDecimal.valueOf(band.getAmount())));
            }
        }

        // If quantity exceeds all bands, apply the last band
        if (!sorted.isEmpty()) {
            RateManagement lastBand = sorted.get(sorted.size() - 1);
            return BigDecimal.valueOf(lastBand.getFlatCost())
                    .add(totalQty.multiply(BigDecimal.valueOf(lastBand.getAmount())));
        }
        return BigDecimal.ZERO;
    }
}
