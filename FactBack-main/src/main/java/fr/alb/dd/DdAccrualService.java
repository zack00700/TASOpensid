package fr.alb.dd;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fr.alb.dd.model.DdAccrual;
import fr.alb.dd.model.DdDayEntry;
import fr.alb.dd.model.DdRule;
import fr.alb.dd.model.DdWaiver;
import fr.alb.dd.model.HolidayCalendar;
import fr.alb.yard.model.Item;
import fr.alb.type.DdAccrualStatus;
import fr.alb.type.DdClockAnchor;
import fr.alb.type.DdType;
import fr.alb.type.WaiverType;

@ApplicationScoped
public class DdAccrualService {

    private static final Logger LOGGER = Logger.getLogger(DdAccrualService.class);

    @Inject
    FreeDayCalculator freeDayCalculator;

    @Inject
    DdRuleResolver ruleResolver;

    @ConfigProperty(name = "app.timezone", defaultValue = "UTC")
    String timezone;

    // -------------------------------------------------------------------------
    // Gate-in: start demurrage accrual
    // -------------------------------------------------------------------------

    /**
     * Called when a container gates IN. Starts DEMURRAGE accrual.
     */
    @Transactional
    public void onGateIn(Item item, String carrierId) {
        Optional<DdRule> ruleOpt = ruleResolver.resolve(DdType.DEMURRAGE, carrierId, item.getContainerType());
        if (ruleOpt.isEmpty()) {
            LOGGER.warnf("onGateIn: no DEMURRAGE rule found for carrier=%s containerType=%s — skipping accrual for item %s",
                    carrierId, item.getContainerType(), item.id);
            return;
        }
        DdRule rule = ruleOpt.get();

        // Skip if a DEMURRAGE accrual already exists for this item
        long existing = DdAccrual.count("itemId = ?1 and ddType = ?2", item.id, DdType.DEMURRAGE);
        if (existing > 0) {
            LOGGER.debugf("onGateIn: DEMURRAGE accrual already exists for item %s — skipping", item.id);
            return;
        }

        DdAccrual accrual = new DdAccrual();
        accrual.itemId = item.id;
        accrual.containerNumber = item.getContainerNumber();
        accrual.ddType = DdType.DEMURRAGE;
        accrual.clockAnchor = rule.clockAnchor;
        accrual.ruleId = rule.id;
        accrual.carrierId = carrierId;
        accrual.freeDaysGranted = rule.freeDays;

        // Determine clockStart based on the clock anchor
        Instant clockStart = resolveClockStart(rule.clockAnchor, item, DdType.DEMURRAGE);
        accrual.clockStart = clockStart;

        if (clockStart == null) {
            // Clock not started yet — create with STOPPED status awaiting the trigger event
            accrual.status = DdAccrualStatus.STOPPED;
        }

        accrual.persist();

        if (clockStart != null) {
            computeAndUpdate(accrual, rule);
        }
    }

    // -------------------------------------------------------------------------
    // Gate-out: stop demurrage, start detention
    // -------------------------------------------------------------------------

    /**
     * Called when a container gates OUT. Stops DEMURRAGE, starts DETENTION.
     */
    @Transactional
    public void onGateOut(Item item, String carrierId) {
        // 1. Stop the running DEMURRAGE accrual if one exists
        List<DdAccrual> demurrageRunning = DdAccrual
                .find("itemId = ?1 and ddType = ?2 and status = ?3",
                        item.id, DdType.DEMURRAGE, DdAccrualStatus.RUNNING)
                .list();

        for (DdAccrual dem : demurrageRunning) {
            dem.clockStop = item.getGateOutDate();
            dem.status = DdAccrualStatus.STOPPED;
            DdRule demRule = DdRule.findById(dem.ruleId);
            if (demRule != null) {
                computeAndUpdate(dem, demRule);
            } else {
                dem.update();
            }
        }

        // 2. Start DETENTION accrual
        Optional<DdRule> ruleOpt = ruleResolver.resolve(DdType.DETENTION, carrierId, item.getContainerType());
        if (ruleOpt.isEmpty()) {
            LOGGER.warnf("onGateOut: no DETENTION rule found for carrier=%s containerType=%s — skipping detention accrual for item %s",
                    carrierId, item.getContainerType(), item.id);
            return;
        }
        DdRule rule = ruleOpt.get();

        // Skip if a DETENTION accrual already exists for this item
        long existing = DdAccrual.count("itemId = ?1 and ddType = ?2", item.id, DdType.DETENTION);
        if (existing > 0) {
            LOGGER.debugf("onGateOut: DETENTION accrual already exists for item %s — skipping", item.id);
            return;
        }

        DdAccrual detention = new DdAccrual();
        detention.itemId = item.id;
        detention.containerNumber = item.getContainerNumber();
        detention.ddType = DdType.DETENTION;
        detention.clockAnchor = rule.clockAnchor;
        detention.ruleId = rule.id;
        detention.carrierId = carrierId;
        detention.freeDaysGranted = rule.freeDays;
        // For detention, the clock starts at gate-out (container leaves the terminal)
        detention.clockStart = item.getGateOutDate();

        detention.persist();

        if (detention.clockStart != null) {
            computeAndUpdate(detention, rule);
        }
    }

    // -------------------------------------------------------------------------
    // Daily tick
    // -------------------------------------------------------------------------

    /**
     * Daily tick: recompute all RUNNING accruals.
     * Called by DdAccrualScheduler. Processes in batches.
     */
    @Transactional
    public int tickAll() {
        List<DdAccrual> running = DdAccrual.find("status", DdAccrualStatus.RUNNING).list();
        int updated = 0;
        for (DdAccrual accrual : running) {
            try {
                DdRule rule = DdRule.findById(accrual.ruleId);
                if (rule == null) {
                    LOGGER.warnf("DD tick: rule %s not found for accrual %s — skipping", accrual.ruleId, accrual.id);
                    continue;
                }
                computeAndUpdate(accrual, rule);
                updated++;
            } catch (Exception e) {
                LOGGER.warnf("DD tick failed for accrual %s: %s", accrual.id, e.getMessage());
            }
        }
        return updated;
    }

    // -------------------------------------------------------------------------
    // Compute & persist
    // -------------------------------------------------------------------------

    /**
     * Recompute dailyLog + totals for an accrual and persist.
     */
    @Transactional
    public void computeAndUpdate(DdAccrual accrual, DdRule rule) {
        if (accrual.clockStart == null) {
            return;
        }

        ZoneId zone = ZoneId.of(getTimezone());
        List<String> holidays = loadHolidays(zone, accrual.clockStart);

        List<DdDayEntry> log = freeDayCalculator.buildDailyLog(
                rule, accrual.clockStart, accrual.clockStop, holidays, zone);

        accrual.dailyLog = log;
        accrual.totalDaysElapsed = log.size();
        accrual.chargeableDays = freeDayCalculator.countChargeableDays(log);
        accrual.holidayDays = (int) log.stream().filter(e -> e.isHoliday).count();
        accrual.freeDaysGranted = rule.freeDays;
        accrual.totalAccruedAmount = freeDayCalculator.sumCharges(log);

        // Subtract waived amounts
        if (accrual.waivers != null && !accrual.waivers.isEmpty()) {
            BigDecimal waivedTotal = accrual.waivers.stream()
                    .map(w -> w.waivedAmount != null ? w.waivedAmount : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            accrual.totalAccruedAmount = accrual.totalAccruedAmount
                    .subtract(waivedTotal)
                    .max(BigDecimal.ZERO);
        }

        accrual.update();
    }

    // -------------------------------------------------------------------------
    // Waiver
    // -------------------------------------------------------------------------

    /**
     * Apply a waiver to an accrual.
     */
    @Transactional
    public DdAccrual applyWaiver(String accrualId, DdWaiver waiver) {
        DdAccrual accrual = DdAccrual.findById(accrualId);
        if (accrual == null) {
            throw new IllegalArgumentException("Accrual not found: " + accrualId);
        }

        waiver.ensureWaiverId();
        if (waiver.approvedAt == null) {
            waiver.approvedAt = Instant.now();
        }

        if (accrual.waivers == null) {
            accrual.waivers = new ArrayList<>();
        }
        accrual.waivers.add(waiver);

        if (waiver.waiverType == WaiverType.FULL) {
            accrual.status = DdAccrualStatus.WAIVED;
        }

        DdRule rule = DdRule.findById(accrual.ruleId);
        if (rule != null) {
            computeAndUpdate(accrual, rule);
        } else {
            accrual.update();
        }

        return accrual;
    }

    // -------------------------------------------------------------------------
    // Mark invoiced
    // -------------------------------------------------------------------------

    /**
     * Mark an accrual as invoiced (called by invoice pipeline).
     */
    @Transactional
    public void markInvoiced(String accrualId, String invoiceId, String invoiceLineId) {
        DdAccrual accrual = DdAccrual.findById(accrualId);
        if (accrual == null) {
            return;
        }
        accrual.status = DdAccrualStatus.INVOICED;
        accrual.invoiceId = invoiceId;
        accrual.invoiceLineId = invoiceLineId;
        accrual.update();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the instant at which the clock should start for the given anchor and item.
     * Returns null when the anchor event has not yet occurred (clock not startable yet).
     */
    private Instant resolveClockStart(DdClockAnchor anchor, Item item, DdType ddType) {
        if (anchor == null) {
            return item.getGateInDate();
        }
        return switch (anchor) {
            case GATE_IN -> item.getGateInDate();
            case DISCHARGE ->
                // Discharge date is not separately tracked on Item; use gateInDate as approximation
                item.getGateInDate();
            case DOCS_READY ->
                // Not available on Item — clock cannot start yet
                null;
            case CUSTOMS_CLEARED ->
                // Not available on Item — clock cannot start yet
                null;
        };
    }

    private String getTimezone() {
        return timezone != null ? timezone : "UTC";
    }

    private List<String> loadHolidays(ZoneId zone, Instant clockStart) {
        int year = clockStart.atZone(zone).getYear();
        List<HolidayCalendar> cals = HolidayCalendar.find("year", year).list();
        return cals.stream()
                .flatMap(c -> c.holidayDates != null ? c.holidayDates.stream() : Stream.empty())
                .collect(Collectors.toList());
    }
}
