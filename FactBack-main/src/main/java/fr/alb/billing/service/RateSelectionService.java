package fr.alb.billing.service;

import fr.alb.billing.model.RateManagement;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stateless service responsible for selecting the most appropriate rate from a
 * contract's rate list given a target date, optional currency, and unit-of-measure.
 *
 * <p>Extracted from {@link fr.alb.dao.InvoiceDaoImpl} — no business logic was
 * changed, only the location of the code.</p>
 */
@ApplicationScoped
public class RateSelectionService {

    private static final Logger LOGGER = Logger.getLogger(RateSelectionService.class);

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    private ZoneId zone() {
        return ZoneId.of(timezone);
    }

    // -------------------------------------------------------------------------
    // String normalisation helpers
    // -------------------------------------------------------------------------

    /** Trims and upper-cases a string; returns {@code null} for blank input. */
    public static String norm(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toUpperCase();
    }

    /**
     * Collapses common DAY-based UoM variants into the canonical token
     * {@code "DAY"}.
     */
    public static String canonicalUom(String s) {
        String u = norm(s);
        if (u == null) return null;
        if (u.equals("DAY") || u.equals("DAYS") || u.equals("PER_DAY") || u.equals("DAILY"))
            return "DAY";
        return u;
    }

    // -------------------------------------------------------------------------
    // Core selection logic
    // -------------------------------------------------------------------------

    /**
     * Selects the best matching {@link RateManagement} entry from {@code rates}
     * for the given {@code date}, optional {@code currency}, and optional
     * {@code uom}.
     *
     * <p>Selection precedence (highest to lowest):
     * <ol>
     *   <li>Exact match on date window, UoM and currency — highest priority / default wins</li>
     *   <li>Date + UoM match with no currency constraint — highest priority / default wins</li>
     *   <li>Any rate flagged as {@code isDefaultRate} — highest priority wins</li>
     * </ol>
     *
     * @param rates    list of candidate rates (may be {@code null} or empty)
     * @param date     the reference date; must not be {@code null}
     * @param currency desired currency code, or {@code null} to skip currency
     *                 filtering
     * @param uom      desired unit-of-measure, or {@code null} to skip UoM
     *                 filtering
     * @return the chosen {@link RateManagement}, or {@code null} if none matched
     */
    public RateManagement selectRate(List<RateManagement> rates, LocalDate date, String currency, String uom) {
        if (rates == null || rates.isEmpty()) {
            if (LOGGER.isDebugEnabled()) LOGGER.debug("[RateSelect] No rates configured");
            return null;
        }

        final String wantUom = canonicalUom(uom);
        final String wantCur = norm(currency);

        List<RateManagement> exact = new ArrayList<>();
        List<RateManagement> relaxedNoCurrency = new ArrayList<>();
        List<RateManagement> defaults = new ArrayList<>();

        for (RateManagement r : rates) {
            LocalDate start = r.getStartDate() == null ? null
                    : r.getStartDate().toInstant().atZone(zone()).toLocalDate();
            LocalDate end = r.getEndDate() == null ? null
                    : r.getEndDate().toInstant().atZone(zone()).toLocalDate();

            boolean dateOk = (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end));
            String ruom = canonicalUom(r.getUnitOfMeasurement());
            String rcur = norm(r.getCurrency());

            boolean uomOk = (wantUom == null) || (ruom == null) || ruom.equals(wantUom);
            boolean curOk = (wantCur == null) || (rcur != null && rcur.equals(wantCur));

            if (r.isDefaultRate()) defaults.add(r);

            if (dateOk && uomOk && curOk) {
                exact.add(r);
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("[RateSelect][skip] amt=%.2f ruom=%s rcur=%s start=%s end=%s :: dateOk=%s uomOk=%s curOk=%s",
                        r.getAmount(), r.getUnitOfMeasurement(), r.getCurrency(),
                        r.getStartDate(), r.getEndDate(), dateOk, uomOk, curOk);
            }

            if (dateOk && uomOk && wantCur != null && !curOk) {
                relaxedNoCurrency.add(r);
            }
        }

        RateManagement chosen = null;
        if (!exact.isEmpty()) {
            chosen = exact.stream()
                    .max(Comparator.comparingInt(RateManagement::getPriority)
                            .thenComparing(r -> r.isDefaultRate() ? 1 : 0))
                    .orElse(exact.get(0));
        } else if (!relaxedNoCurrency.isEmpty()) {
            chosen = relaxedNoCurrency.stream()
                    .max(Comparator.comparingInt(RateManagement::getPriority)
                            .thenComparing(r -> r.isDefaultRate() ? 1 : 0))
                    .orElse(relaxedNoCurrency.get(0));
        } else if (!defaults.isEmpty()) {
            chosen = defaults.stream()
                    .max(Comparator.comparingInt(RateManagement::getPriority))
                    .orElse(defaults.get(0));
        }

        if (chosen == null) {
            if (LOGGER.isDebugEnabled()) LOGGER.debugf("[RateSelect] No rate for date=%s uom=%s cur=%s", date, wantUom, wantCur);
            return null;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[RateSelect] Chosen: amt=%.2f uom=%s cur=%s start=%s end=%s priority=%d default=%s",
                    chosen.getAmount(), chosen.getUnitOfMeasurement(), chosen.getCurrency(),
                    chosen.getStartDate(), chosen.getEndDate(), chosen.getPriority(), chosen.isDefaultRate());
        }
        return chosen;
    }

    /**
     * Filters {@code rates} to those eligible for the given {@code category} and
     * {@code freightKind}.  A rate is eligible when its {@code applicableCategory} is
     * {@code null} (applies to all) or matches {@code category} (case-insensitive), AND
     * its {@code applicableFreightKind} is {@code null} or matches {@code freightKind}.
     *
     * <p>If the filtered list is empty, falls back to rates that have <em>no</em>
     * constraints on either field, preserving backward compatibility with legacy contracts.
     *
     * <p>This method is public-static so that band/tier calculators can reuse it to
     * filter their rate tables before searching for the matching band/tier.
     *
     * @param rates       candidate rate list (may be {@code null})
     * @param category    item category (e.g. "Import") or {@code null}
     * @param freightKind item freight kind (e.g. "FCL") or {@code null}
     * @return filtered list (never {@code null}, may be empty)
     */
    public static List<RateManagement> filterEligibleRates(List<RateManagement> rates,
                                                           String category, String freightKind) {
        if (rates == null || rates.isEmpty()) return Collections.emptyList();

        final String wantCat = norm(category);
        final String wantFk  = norm(freightKind);

        List<RateManagement> filtered = rates.stream()
            .filter(r -> {
                String rc  = norm(r.getApplicableCategory());
                String rfk = norm(r.getApplicableFreightKind());
                boolean catOk = (rc == null)  || (wantCat != null && rc.equals(wantCat));
                boolean fkOk  = (rfk == null) || (wantFk  != null && rfk.equals(wantFk));
                return catOk && fkOk;
            })
            .collect(Collectors.toList());

        // Fallback: use unconstrained rates if no specific match survived the filter
        if (filtered.isEmpty()) {
            filtered = rates.stream()
                .filter(r -> r.getApplicableCategory() == null && r.getApplicableFreightKind() == null)
                .collect(Collectors.toList());
        }
        return filtered;
    }

    /**
     * Variant of {@link #selectRate(List, LocalDate, String, String)} that additionally
     * filters rates by {@code category} and {@code freightKind} before running the core
     * selection logic.  See {@link #filterEligibleRates} for filtering semantics.
     *
     * @param category    item category string (e.g. "Import", "Export") or {@code null}
     * @param freightKind item freight kind string (e.g. "FCL", "LCL") or {@code null}
     */
    public RateManagement selectRate(List<RateManagement> rates, LocalDate date, String currency,
                                     String uom, String category, String freightKind) {
        if (rates == null || rates.isEmpty()) return null;

        List<RateManagement> filtered = filterEligibleRates(rates, category, freightKind);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("[RateSelect] category=%s freightKind=%s → %d candidate(s) after filter",
                    norm(category), norm(freightKind), filtered.size());
        }

        return selectRate(filtered, date, currency, uom);
    }
}
