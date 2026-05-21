package fr.alb.edi;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Stateless utility class for parsing fields out of flat / delimited EDI
 * segments.  No CDI — call all methods statically.
 */
public final class EdiFieldExtractor {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmm");

    private EdiFieldExtractor() {
        // utility class — no instances
    }

    /**
     * Extract a field from a colon-delimited segment by zero-based index.
     * Returns {@code null} when the index is out of range or the field is blank.
     *
     * @param segment the raw segment string (colon-separated)
     * @param index   zero-based field position
     * @return the trimmed field value, or {@code null}
     */
    public static String field(String segment, int index) {
        if (segment == null) {
            return null;
        }
        String[] parts = segment.split(":", -1);
        if (index < 0 || index >= parts.length) {
            return null;
        }
        String value = parts[index].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Parse an EDIFACT date in {@code yyyyMMdd} format to a {@link LocalDate}.
     * Returns {@code null} when the input is blank or cannot be parsed.
     *
     * @param yyyymmdd 8-character date string
     * @return parsed date, or {@code null}
     */
    public static LocalDate parseDate(String yyyymmdd) {
        String s = clean(yyyymmdd);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parse an EDIFACT time in {@code HHmm} format to a {@link LocalTime}.
     * Returns {@code null} when the input is blank or cannot be parsed.
     *
     * @param hhmm 4-character time string
     * @return parsed time, or {@code null}
     */
    public static LocalTime parseTime(String hhmm) {
        String s = clean(hhmm);
        if (s == null) {
            return null;
        }
        try {
            return LocalTime.parse(s, TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Trim a string and return {@code null} if the result is empty.
     *
     * @param s the string to clean
     * @return trimmed string, or {@code null} if blank
     */
    public static String clean(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Parse a numeric string to a {@link Double}.
     * Returns {@code null} when the input is blank or not a valid number.
     *
     * @param s the string to parse
     * @return parsed value, or {@code null}
     */
    public static Double parseDouble(String s) {
        String cleaned = clean(s);
        if (cleaned == null) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
