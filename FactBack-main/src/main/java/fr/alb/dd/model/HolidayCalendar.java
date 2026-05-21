package fr.alb.dd.model;

import fr.alb.model.EntityBase;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Port or country holiday calendar used to exclude non-working days
 * from D&D free-day consumption.
 */
@MongoEntity(collection = "HOLIDAY_CALENDAR")
public class HolidayCalendar extends EntityBase {

    private static final long serialVersionUID = 1L;

    /** Display name for this calendar, e.g. "France 2025". */
    public String calendarName;

    /** ISO 3166-1 alpha-2 country code, e.g. "FR". */
    public String countryCode;

    /** Port code this calendar is scoped to; null means country-wide. */
    public String portCode;

    /** Calendar year this set of holidays covers. */
    public int year;

    /** Holiday dates as ISO strings in "yyyy-MM-dd" format. */
    public List<String> holidayDates;

    /** Optional internal notes. */
    public String notes;

    public HolidayCalendar() {
        super();
        this.holidayDates = new ArrayList<>();
    }
}
