package fr.alb.dd;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import fr.alb.dd.model.DdDayEntry;
import fr.alb.dd.model.DdRule;
import fr.alb.billing.model.RateManagement;

@ApplicationScoped
public class FreeDayCalculator {

    /**
     * Build the full daily log from clockStart to clockStop (or "now" if still running).
     *
     * @param rule         DdRule with freeDays, includeHolidays, includeWeekends, tiers
     * @param clockStart   when clock started
     * @param clockStop    when clock stopped (null = Instant.now())
     * @param holidayDates ISO date strings (yyyy-MM-dd) that are holidays
     * @param zoneId       terminal timezone for day boundary calculation
     * @return list of DdDayEntry, one per calendar day elapsed
     */
    public List<DdDayEntry> buildDailyLog(DdRule rule, Instant clockStart, Instant clockStop,
                                           List<String> holidayDates, ZoneId zoneId) {
        List<DdDayEntry> log = new ArrayList<>();
        if (clockStart == null) {
            return log;
        }

        Instant endInstant = clockStop != null ? clockStop : Instant.now();

        // Determine the first and last calendar day in the terminal's timezone
        LocalDate startDay = clockStart.atZone(zoneId).toLocalDate();
        LocalDate endDay = endInstant.atZone(zoneId).toLocalDate();

        int dayNumber = 0;
        LocalDate current = startDay;

        while (!current.isAfter(endDay)) {
            dayNumber++;

            String dateStr = current.toString(); // yyyy-MM-dd
            boolean isHoliday = holidayDates != null && holidayDates.contains(dateStr);
            boolean isWeekend = isWeekend(current);

            // A day is free if:
            //   - it is within the freeDays count, AND
            //   - holidays are included OR this day is not a holiday, AND
            //   - weekends are included OR this day is not a weekend
            boolean consumesFreeDay = dayNumber <= rule.freeDays
                    && (rule.includeHolidays || !isHoliday)
                    && (rule.includeWeekends || !isWeekend);

            // A day is free (no charge) if it consumes a free slot, or it is excluded from
            // free-day counting AND the rule does not include it in charges either.
            // Simpler model: a day is free if dayNumber <= freeDays accounting for inclusions.
            boolean isFreeDay;
            BigDecimal chargeAmount;
            String rateBandLabel;

            if (consumesFreeDay) {
                isFreeDay = true;
                chargeAmount = BigDecimal.ZERO;
                rateBandLabel = "Free Day";
            } else {
                // Count how many actual chargeable days have passed before this one
                // to find the position in the tier structure.
                // chargeableDayNum = count of non-free days up to and including this day.
                // We compute it from what we have already logged.
                int chargeableDayNum = countChargeableDaysUpTo(log) + 1;

                // Check if excluded by holiday/weekend rules (but free days exhausted):
                // Per the spec, once free days are exhausted, holidays/weekends still count
                // toward charges unless the rule explicitly skips them. The spec says
                // isFreeDay = dayNumber <= freeDays AND ..., so days beyond freeDays with
                // holiday/weekend flags are still chargeable.
                isFreeDay = false;
                chargeAmount = computeTierCharge(rule.tiers, chargeableDayNum);
                rateBandLabel = resolveTierLabel(rule.tiers, chargeableDayNum);
            }

            DdDayEntry entry = new DdDayEntry();
            entry.date = current.atStartOfDay(zoneId).toInstant();
            entry.dayNumber = dayNumber;
            entry.isFreeDay = isFreeDay;
            entry.isHoliday = isHoliday;
            entry.chargeAmount = chargeAmount;
            entry.rateBandLabel = rateBandLabel;

            log.add(entry);
            current = current.plusDays(1);
        }

        return log;
    }

    /**
     * Compute charge for a given chargeable day number using tiered rates from RateManagement.
     * A tier covers startQuantity..endQuantity days.
     * Find the matching tier; if none, use the last tier's amount.
     * endQuantity == 0 means "open-ended" (last tier).
     */
    private BigDecimal computeTierCharge(List<RateManagement> tiers, int chargeableDayNum) {
        if (tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }

        RateManagement lastTier = null;
        for (RateManagement tier : tiers) {
            lastTier = tier;
            double start = tier.getStartQuantity();
            double end = tier.getEndQuantity();
            if (chargeableDayNum >= start && (end == 0 || chargeableDayNum <= end)) {
                return BigDecimal.valueOf(tier.getAmount());
            }
        }
        // Fallback: use last tier amount (open-ended)
        return lastTier != null ? BigDecimal.valueOf(lastTier.getAmount()) : BigDecimal.ZERO;
    }

    /**
     * Resolve a human-readable tier label for a chargeable day number.
     */
    private String resolveTierLabel(List<RateManagement> tiers, int chargeableDayNum) {
        if (tiers == null || tiers.isEmpty()) {
            return "No Rate";
        }
        RateManagement lastTier = null;
        for (RateManagement tier : tiers) {
            lastTier = tier;
            double start = tier.getStartQuantity();
            double end = tier.getEndQuantity();
            if (chargeableDayNum >= start && (end == 0 || chargeableDayNum <= end)) {
                return buildTierLabel(tier);
            }
        }
        return lastTier != null ? buildTierLabel(lastTier) : "No Rate";
    }

    private String buildTierLabel(RateManagement tier) {
        String start = formatQty(tier.getStartQuantity());
        double end = tier.getEndQuantity();
        String currency = tier.getCurrency() != null ? tier.getCurrency() : "";
        if (end == 0) {
            return start + "+: " + currency + tier.getAmount() + "/day";
        }
        return start + "-" + formatQty(end) + ": " + currency + tier.getAmount() + "/day";
    }

    private String formatQty(double qty) {
        // Show as integer when it is a whole number
        if (qty == Math.floor(qty)) {
            return String.valueOf((int) qty);
        }
        return String.valueOf(qty);
    }

    /** Count the non-free entries already accumulated in the partial log. */
    private int countChargeableDaysUpTo(List<DdDayEntry> partialLog) {
        return (int) partialLog.stream().filter(e -> !e.isFreeDay).count();
    }

    private boolean isWeekend(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        };
    }

    public int countChargeableDays(List<DdDayEntry> log) {
        return (int) log.stream().filter(e -> !e.isFreeDay).count();
    }

    public BigDecimal sumCharges(List<DdDayEntry> log) {
        return log.stream()
                  .map(e -> e.chargeAmount != null ? e.chargeAmount : BigDecimal.ZERO)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
