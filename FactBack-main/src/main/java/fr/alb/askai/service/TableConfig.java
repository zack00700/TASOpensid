package fr.alb.askai.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public interface TableConfig {
    String getCollectionName();
    Map<String, String> getFieldAliases();
    List<String> getDefaultColumns();
    String getDateField();
    DateRange getDefaultDateRange(ZoneId zone);
    String getDisplayName();
}
