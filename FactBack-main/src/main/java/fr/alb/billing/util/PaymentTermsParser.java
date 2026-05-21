package fr.alb.billing.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses payment-terms strings like "NET30" / "NET60" into a day count.
 * Returns 30 for any null, empty, or unrecognized input — that is the
 * default term used when a customer's terms are missing or malformed.
 */
public final class PaymentTermsParser {

    private static final int DEFAULT_DAYS = 30;
    private static final Pattern NET_PATTERN = Pattern.compile("^NET(\\d+)$", Pattern.CASE_INSENSITIVE);

    public static int parseDays(String terms) {
        if (terms == null) return DEFAULT_DAYS;
        String trimmed = terms.trim();
        if (trimmed.isEmpty()) return DEFAULT_DAYS;
        Matcher m = NET_PATTERN.matcher(trimmed);
        if (!m.matches()) return DEFAULT_DAYS;
        try {
            int days = Integer.parseInt(m.group(1));
            return days > 0 ? days : DEFAULT_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_DAYS;
        }
    }

    private PaymentTermsParser() {}
}
