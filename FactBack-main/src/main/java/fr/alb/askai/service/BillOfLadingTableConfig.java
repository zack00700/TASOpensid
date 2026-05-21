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
@Named("billOfLadingConfig")
public class BillOfLadingTableConfig implements TableConfig {

    @ConfigProperty(name = "app.bill-of-lading.collection", defaultValue = "BillOfLading")
    String collectionName;

    @ConfigProperty(name = "app.bill-of-lading.date-range-years", defaultValue = "1")
    int dateRangeYears;

    private static final Map<String, String> FIELD_ALIASES = Map.ofEntries(
            // Date aliases
            Map.entry("date", "createdAt"),
            Map.entry("shippingDate", "createdAt"),
            Map.entry("shipped", "createdAt"),
            Map.entry("loadDate", "createdAt"),
            Map.entry("blDate", "createdAt"),

            // Number aliases
            Map.entry("number", "blNumber"),
            Map.entry("billNumber", "blNumber"),
            Map.entry("bol", "blNumber"),
            Map.entry("bill", "blNumber"),

            // Party aliases
            Map.entry("shipperName", "shipper"),
            Map.entry("consigneeName", "consignee"),
            Map.entry("notify", "notifyParty"),

            // Port aliases
            Map.entry("origin", "portOfLoading"),
            Map.entry("originPort", "portOfLoading"),
            Map.entry("loadingPort", "portOfLoading"),
            Map.entry("pol", "portOfLoading"),

            Map.entry("destination", "portOfDischarge"),
            Map.entry("destinationPort", "portOfDischarge"),
            Map.entry("dischargePort", "portOfDischarge"),
            Map.entry("pod", "portOfDischarge"),
            Map.entry("discharge", "portOfDischarge"),

            Map.entry("delivery", "placeOfDelivery"),
            Map.entry("deliveryPlace", "placeOfDelivery"),

            // Transport aliases
            Map.entry("vesselName", "vessel"),
            Map.entry("ship", "vessel"),

            // ✅ Commodity aliases - AVEC HAZARDOUS
            Map.entry("weight", "commodity.weightKg"),
            Map.entry("volume", "commodity.volumeM3"),
            Map.entry("packages", "commodity.packagesNumber"),
            Map.entry("description", "commodity.description"),
            Map.entry("hazardous", "commodity.hazardous"),        // ✅ AJOUTÉ
            Map.entry("dangerous", "commodity.hazardous"),        // ✅ ALIAS
            Map.entry("hazard", "commodity.hazardous")            // ✅ ALIAS
    );

    private static final List<String> DEFAULT_COLUMNS =
            List.of("blNumber", "createdAt", "shipper", "consignee", "portOfLoading", "portOfDischarge", "status");

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public Map<String, String> getFieldAliases() {
        return FIELD_ALIASES;
    }

    @Override
    public List<String> getDefaultColumns() { return DEFAULT_COLUMNS; }

    @Override
    public String getDateField() { return "createdAt"; }

    @Override
    public String getDisplayName() { return "Bill of Lading"; }

    @Override
    public DateRange getDefaultDateRange(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        return new DateRange(
                now.minusYears(1).truncatedTo(ChronoUnit.DAYS),
                now.plusMonths(1).truncatedTo(ChronoUnit.DAYS)
        );
    }
}