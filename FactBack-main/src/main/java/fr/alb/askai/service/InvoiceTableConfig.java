package fr.alb.askai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Named("invoiceConfig")
public class InvoiceTableConfig implements TableConfig {

    @ConfigProperty(name = "app.invoice.collection", defaultValue = "INVOICE")
    String collectionName;

    @ConfigProperty(name = "app.invoice.date-range-years", defaultValue = "1")
    int dateRangeYears;

    private static final Map<String, String> FIELD_ALIASES = Map.of(
            "date", "createdDate",
            "invoiceDate", "createdDate",
            "issuedAt", "createdDate",
            "createdAt", "createdDate",
            "number", "finalNumber",
            "invoiceNumber", "finalNumber",
            "customer", "customerName",
            "client", "customerName",
            "totalAmount", "amount"
    );

    private static final List<String> DEFAULT_COLUMNS =
            List.of("finalNumber", "createdDate", "customerName", "amount", "status");

    @Override
    public String getCollectionName() { return collectionName; }

    @Override
    public Map<String, String> getFieldAliases() { return FIELD_ALIASES; }

    @Override
    public List<String> getDefaultColumns() { return DEFAULT_COLUMNS; }

    @Override
    public String getDateField() { return "createdDate"; }

    @Override
    public String getDisplayName() { return "Invoice"; }

    @Override
    public DateRange getDefaultDateRange(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        return new DateRange(
                now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS),
                now.plusYears(dateRangeYears).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
        );
    }
}
