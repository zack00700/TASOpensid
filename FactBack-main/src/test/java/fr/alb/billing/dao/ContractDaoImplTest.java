package fr.alb.billing.dao;

import fr.alb.billing.model.Contract;
import fr.alb.billing.testutil.ContractFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContractDaoImplTest {

    @Inject
    ContractDaoImpl dao;

    @BeforeEach
    void clean() {
        Contract.deleteAll();
        fr.alb.billing.model.Tariff.deleteAll();
    }

    @Test
    void addContract_persistsAndAssignsId() {
        Contract c = ContractFixtures.aContract();

        dao.addContract(c);

        assertNotNull(c.getId());
        Contract found = dao.findContract(c.getId());
        assertNotNull(found);
        assertEquals(c.name, found.name);
    }

    @Test
    void addContract_throws_whenNameAlreadyExists() {
        Contract first = ContractFixtures.aContract();
        first.name = "Duplicate Name";
        dao.addContract(first);

        Contract second = ContractFixtures.aContract();
        second.name = "Duplicate Name";

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> dao.addContract(second));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void findContract_autogeneratesMissingRateIds() {
        Contract c = ContractFixtures.aContract();
        c.rates.get(0).setRateId(null);
        dao.addContract(c);

        Contract found = dao.findContract(c.getId());
        assertNotNull(found.rates.get(0).getRateId(),
            "findContract triggers ensureRateIdentifiers, matching findActiveContracts/getContracts");
    }

    @Test
    void findContract_hydratesFromTariff_whenContractHasTariffIdAndNoEmbeddedRates() {
        fr.alb.billing.model.Tariff tariff = ContractFixtures.aTariff();
        tariff.rates.get(0).setAmount(77.0);
        tariff.persist();

        Contract c = ContractFixtures.aContract();
        c.tariffId = tariff.getId();
        c.rates = null;
        dao.addContract(c);

        Contract found = dao.findContract(c.getId());
        assertNotNull(found.rates);
        assertEquals(77.0, found.rates.get(0).getAmount(),
            "findContract hydrates the linked Tariff's rates when the contract has none");
    }

    @Test
    void findActiveContracts_autogeneratesMissingRateIds() {
        Contract c = ContractFixtures.aContract();
        c.rates.get(0).setRateId(null);
        dao.addContract(c);

        java.util.List<Contract> result = dao.findActiveContracts();
        Contract loaded = result.stream()
            .filter(x -> x.name.equals(c.name))
            .findFirst()
            .orElseThrow();
        assertNotNull(loaded.rates.get(0).getRateId(),
            "findActiveContracts triggers ensureRateIdentifiers");
    }

    @Test
    void findActiveContracts_returnsOnlyActiveStatus() {
        Contract active = ContractFixtures.aContract();
        active.status = fr.alb.type.Status.ACTIVE;
        Contract disabled = ContractFixtures.aContract();
        disabled.status = fr.alb.type.Status.DISABLE;
        dao.addContract(active);
        dao.addContract(disabled);

        java.util.List<Contract> result = dao.findActiveContracts();
        assertTrue(result.stream().anyMatch(c -> c.name.equals(active.name)));
        assertFalse(result.stream().anyMatch(c -> c.name.equals(disabled.name)));
    }

    @Test
    void hydrateFromTariff_copiesTariffRates_whenContractHasTariffIdAndNoEmbeddedRates() {
        fr.alb.billing.model.Tariff tariff = ContractFixtures.aTariff();
        tariff.rates.get(0).setAmount(123.45);
        tariff.persist();

        Contract c = ContractFixtures.aContract();
        c.tariffId = tariff.getId();
        c.rates = null; // no embedded rates
        dao.addContract(c);

        Contract hydrated = dao.findActiveContracts().stream()
            .filter(x -> x.name.equals(c.name))
            .findFirst()
            .orElseThrow();
        assertNotNull(hydrated.rates);
        assertFalse(hydrated.rates.isEmpty());
        assertEquals(123.45, hydrated.rates.get(0).getAmount());
    }

    @Test
    void hydrateFromTariff_keepsEmbeddedRates_whenContractAlreadyHasRates() {
        fr.alb.billing.model.Tariff tariff = ContractFixtures.aTariff();
        tariff.rates.get(0).setAmount(999.0);
        tariff.persist();

        Contract c = ContractFixtures.aContract();
        c.tariffId = tariff.getId();
        c.rates.get(0).setAmount(50.0); // contract's own rate
        dao.addContract(c);

        Contract hydrated = dao.findActiveContracts().stream()
            .filter(x -> x.name.equals(c.name))
            .findFirst()
            .orElseThrow();
        assertEquals(50.0, hydrated.rates.get(0).getAmount(), "embedded rates take priority over Tariff");
    }
}
