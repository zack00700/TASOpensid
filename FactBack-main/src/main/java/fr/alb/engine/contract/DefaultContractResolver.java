package fr.alb.engine.contract;

import fr.alb.billing.dao.ContractDao;
import fr.alb.billing.model.Contract;
import fr.alb.model.Event;
import fr.alb.yard.model.Item;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.service.RateSelectionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of ContractResolver.
 * Fetches active contracts and selects the one whose rate best matches
 * the item's UoM and billing date.
 *
 * Replace this with a more sophisticated resolver when contract selection
 * becomes multi-tenant or customer-specific.
 */
@ApplicationScoped
public class DefaultContractResolver implements ContractResolver {

    private static final Logger LOGGER = Logger.getLogger(DefaultContractResolver.class);

    @Inject
    ContractDao contractDao;

    @Inject
    RateSelectionService rateSelector;

    @Override
    public Optional<ContractMatch> resolve(Item item, Event event, LocalDate date) {
        List<Contract> active = contractDao.findActiveContracts();

        for (Contract contract : active) {
            if (contract.rates == null || contract.rates.isEmpty()) continue;

            String uom = resolveUom(contract);

            // Item has no currency field; pass null so rate selection skips currency filtering
            RateManagement rate = rateSelector.selectRate(contract.rates, date, null, uom,
                item.getCategory() != null ? item.getCategory().getValue() : null,
                item.getFreightKind() != null ? item.getFreightKind().getValue() : null);
            if (rate == null) continue;

            String reason = "Contract '" + contract.name + "' matched — rate priority=" + rate.getPriority()
                + ", UoM=" + uom;
            LOGGER.debugf("ContractResolver: %s", reason);
            return Optional.of(new ContractMatch(contract, rate, reason));
        }

        LOGGER.debugf("ContractResolver: no match found for item %s on %s", item.id, date);
        return Optional.empty();
    }

    private String resolveUom(Contract contract) {
        if (contract.calculationMode == null) return "DAY";
        // infer UoM from calculation mode type
        return switch (contract.calculationMode.type) {
            case DATE, DATE_BY_TEU -> "DAY";
            default -> "unit";
        };
    }
}
