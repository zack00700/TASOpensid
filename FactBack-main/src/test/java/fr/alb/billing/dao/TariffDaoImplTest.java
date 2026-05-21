package fr.alb.billing.dao;

import fr.alb.billing.model.Tariff;
import fr.alb.billing.testutil.ContractFixtures;
import fr.alb.type.ServiceType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TariffDaoImplTest {

    @Inject
    TariffDaoImpl dao;

    @BeforeEach
    void clean() {
        Tariff.deleteAll();
    }

    @Test
    void addAndFindTariff_roundTripsPersistedFields() {
        Tariff t = ContractFixtures.aTariff();
        t.description = "Round-trip me";

        dao.addTariff(t);

        Tariff found = dao.findTariff(t.getId());
        assertNotNull(found);
        assertEquals("Round-trip me", found.description);
        assertEquals(t.name, found.name);
    }

    @Test
    void findByServiceType_returnsOnlyMatchingServiceType() {
        Tariff storage = ContractFixtures.aTariff();
        storage.serviceType = ServiceType.STORAGE;
        Tariff handling = ContractFixtures.aTariff();
        handling.serviceType = ServiceType.HANDLING;
        dao.addTariff(storage);
        dao.addTariff(handling);

        List<Tariff> result = dao.findByServiceType(ServiceType.STORAGE);
        assertTrue(result.stream().anyMatch(x -> x.name.equals(storage.name)));
        assertFalse(result.stream().anyMatch(x -> x.name.equals(handling.name)));
    }
}
