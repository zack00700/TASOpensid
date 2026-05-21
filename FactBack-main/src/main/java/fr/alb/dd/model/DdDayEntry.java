package fr.alb.dd.model;

import fr.alb.model.EntityBase;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-day audit entry embedded in a DdAccrual daily log.
 * Plain POJO — not a Mongo entity on its own.
 */
public class DdDayEntry {

    /** UTC start of the calendar day this entry represents. */
    public Instant date;

    /** 1-based index of this day within the accrual period. */
    public int dayNumber;

    /** True if this day is covered by the free-day allowance or is a holiday. */
    public boolean isFreeDay;

    /** True if this day falls on a holiday from the HolidayCalendar. */
    public boolean isHoliday;

    /** Charge amount for this day; zero when free or holiday. */
    public BigDecimal chargeAmount;

    /** Human-readable rate band label, e.g. "1-5: $30/day". */
    public String rateBandLabel;

    /** Optional explanatory note. */
    public String note;

    public DdDayEntry() {}
}
