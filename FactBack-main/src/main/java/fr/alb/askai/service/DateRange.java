package fr.alb.askai.service;

import java.time.ZonedDateTime;
import java.util.Objects;

public class DateRange {
    private final ZonedDateTime from;
    private final ZonedDateTime to;

    public DateRange(ZonedDateTime from, ZonedDateTime to) {
        this.from = Objects.requireNonNull(from, "from date cannot be null");
        this.to = Objects.requireNonNull(to, "to date cannot be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from date must be before or equal to to date");
        }
    }

    public ZonedDateTime from() {
        return from;
    }

    public ZonedDateTime to() {
        return to;
    }

    public ZonedDateTime getFrom() {
        return from;
    }

    public ZonedDateTime getTo() {
        return to;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DateRange dateRange = (DateRange) obj;
        return Objects.equals(from, dateRange.from) && Objects.equals(to, dateRange.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return "DateRange{from=" + from + ", to=" + to + '}';
    }
}