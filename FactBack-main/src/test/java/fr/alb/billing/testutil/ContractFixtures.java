package fr.alb.billing.testutil;

import fr.alb.billing.model.*;
import fr.alb.type.CalculationModeType;
import fr.alb.type.ServiceType;
import fr.alb.type.Status;
import fr.alb.yard.model.EventConfig;

import java.time.Instant;
import java.util.*;

/**
 * Builders for contracts-domain entities. Each factory returns a fully-populated
 * object with sensible defaults; tests override only the fields they care about.
 */
public final class ContractFixtures {

    private ContractFixtures() {}

    public static EventConfig anEvent() {
        EventConfig e = new EventConfig();
        e.setId("evt-default");
        e.setEventName("Storage");
        return e;
    }

    public static CalculationMode aCalculationMode() {
        CalculationMode m = new CalculationMode();
        m.eventConfig = anEvent();
        m.type = CalculationModeType.QUANTITY;
        m.subType = "in_date";
        m.filters = new ArrayList<>();
        return m;
    }

    public static RateManagement aRate() {
        RateManagement r = new RateManagement();
        r.setRateId(UUID.randomUUID().toString());
        r.setStartQuantity(0);
        r.setEndQuantity(100);
        r.setUnitOfMeasurement("DAY");
        r.setAmount(50.0);
        r.setCurrency("EUR");
        r.setPriority(0);
        return r;
    }

    public static Contract aContract() {
        Contract c = new Contract();
        c.name = "Test Contract " + UUID.randomUUID().toString().substring(0, 8);
        c.description = "Test";
        c.status = Status.ACTIVE;
        c.startDate = java.util.Date.from(Instant.parse("2026-01-01T00:00:00Z"));
        c.endDate = java.util.Date.from(Instant.parse("2026-12-31T23:59:59Z"));
        c.calculationMode = aCalculationMode();
        c.rates = new ArrayList<>(List.of(aRate()));
        c.priority = 0;
        return c;
    }

    public static Tariff aTariff() {
        Tariff t = new Tariff();
        t.name = "Test Tariff " + UUID.randomUUID().toString().substring(0, 8);
        t.description = "Test";
        t.status = Status.ACTIVE;
        t.serviceType = ServiceType.STORAGE;
        t.startDate = java.util.Date.from(Instant.parse("2026-01-01T00:00:00Z"));
        t.endDate = java.util.Date.from(Instant.parse("2026-12-31T23:59:59Z"));
        t.calculationMode = aCalculationMode();
        t.rates = new ArrayList<>(List.of(aRate()));
        return t;
    }

    public static ContractAddendum anAddendum() {
        ContractAddendum a = new ContractAddendum();
        a.setAddendumId(UUID.randomUUID().toString());
        a.setValidFrom(Instant.parse("2026-06-01T00:00:00Z"));
        a.setValidTo(Instant.parse("2026-09-01T00:00:00Z"));
        a.setRateOverrides(new ArrayList<>());
        return a;
    }
}
