package fr.alb.billing.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import fr.alb.billing.dao.ContractDao;
import fr.alb.dto.InvoiceLineDiagnostic;
import fr.alb.billing.model.Contract;
import fr.alb.model.Event;
import fr.alb.billing.model.Invoice;
import fr.alb.yard.model.Item;
import fr.alb.billing.model.RateManagement;
import fr.alb.type.CalculationModeType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InvoiceComputationService {

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone = "Europe/Paris";

    @Inject InvoiceCalculationService invoiceCalculationService;
    @Inject RateSelectionService rateSelectionService;
    @Inject ContractDao contractDao;

    public List<InvoiceLineDiagnostic> computeDiagnostics(Invoice inv) {
        List<InvoiceLineDiagnostic> diags = new ArrayList<>();
        ZoneId z = ZoneId.of(timezone);
        LocalDate invoiceDate = inv.createdDate != null ? inv.createdDate : LocalDate.now(z);

        // 1) Resolve scope (same order your pipeline uses)
        List<Item> items = Item.list("relatedInvoice", inv.id);
        String scope = "relatedInvoice";
        if (items.isEmpty() && inv.itemIds != null && !inv.itemIds.isEmpty()) {
            items = Item.list("_id in ?1", inv.itemIds);
            scope = "itemIds";
        }
        if (items.isEmpty() && inv.billOfLadingId != null) {
            items = Item.list("billOfLadingId", inv.billOfLadingId);
            scope = "billOfLadingId";
        }
        if (items.isEmpty()) {
            diags.add(new InvoiceLineDiagnostic(null, null, "SCOPE", "No items resolved by any scope", null, null, null, null, null));
            return diags;
        }

        // 2) Filter contracts: DATE / in_date
        List<Contract> active = contractDao.findActiveContracts();
        List<Contract> dateContracts = active.stream()
            .filter(c -> c.calculationMode != null
                      && c.calculationMode.type == CalculationModeType.DATE
                      && c.calculationMode.subType != null
                      && "in_date".equalsIgnoreCase(c.calculationMode.subType))
            .toList();
        if (dateContracts.isEmpty()) {
            diags.add(new InvoiceLineDiagnostic(null, null, "CONTRACT", "No active DATE/in_date contracts", null, null, null, null, null));
            return diags;
        }

        for (Item item : items) {
            // 3) IN event
            Event inEvt = null;
            try {
                inEvt = invoiceCalculationService.findSingleInEvent(item);
            } catch (Exception ex) {
                diags.add(new InvoiceLineDiagnostic(item.getId(), item.getItemNumber(), "IN_EVENT", "Multiple IN events", null, null, null, null, null));
                continue;
            }
            if (inEvt == null) {
                diags.add(new InvoiceLineDiagnostic(item.getId(), item.getItemNumber(), "IN_EVENT", "No IN event found", null, null, null, null, null));
                continue;
            }

            LocalDate inDate = inEvt.getTimeStamp().toInstant().atZone(z).toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(inDate, invoiceDate);
            if (days <= 0) {
                diags.add(new InvoiceLineDiagnostic(item.getId(), item.getItemNumber(), "DAYS", "Non-positive day count", inDate, days, null, null, null));
                continue;
            }

            boolean anyRate = false;
            for (Contract c : dateContracts) {
                RateManagement rate = rateSelectionService.selectRate(c.rates, invoiceDate, null, "DAY",
                    item.getCategory() != null ? item.getCategory().getValue() : null,
                    item.getFreightKind() != null ? item.getFreightKind().getValue() : null);
                if (rate == null) {
                    diags.add(new InvoiceLineDiagnostic(item.getId(), item.getItemNumber(), "RATE",
                        "No applicable rate on invoiceDate", inDate, days, c.getId(), "DAY", null));
                    continue;
                }
                anyRate = true; // found at least one usable contract+rate
                break;
            }
            if (!anyRate) {
                diags.add(new InvoiceLineDiagnostic(item.getId(), item.getItemNumber(), "RATE",
                    "No rate across all DATE/in_date contracts", inDate, days, null, "DAY", null));
            }
        }

        diags.add(new InvoiceLineDiagnostic(null, null, "SUMMARY", "Resolved items via: " + scope, null, null, null, null, null));
        return diags;
    }
}
