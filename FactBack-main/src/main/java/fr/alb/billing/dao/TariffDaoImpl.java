package fr.alb.billing.dao;

import java.util.List;

import fr.alb.billing.model.RateManagement;
import fr.alb.billing.model.Tariff;
import fr.alb.type.ServiceType;
import fr.alb.type.Status;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TariffDaoImpl implements PanacheMongoRepository<Tariff>, TariffDao {

    @Override
    @Transactional
    @CacheInvalidateAll(cacheName = "active-tariffs")
    public void addTariff(Tariff tariff) {
        if (tariff == null) throw new IllegalArgumentException("tariff cannot be null");
        persist(tariff);
        ensureRateIdentifiers(tariff);
    }

    @Override
    public Tariff findTariff(String tariffId) {
        if (tariffId == null || tariffId.isBlank()) {
            throw new IllegalArgumentException("tariffId cannot be null or blank");
        }
        return find("_id", tariffId.trim()).firstResult();
    }

    @Override
    @CacheResult(cacheName = "active-tariffs")
    public List<Tariff> getActiveTariffs() {
        List<Tariff> tariffs = list("status", Status.ACTIVE);
        tariffs.forEach(this::ensureRateIdentifiers);
        return tariffs;
    }

    @Override
    public List<Tariff> findByServiceType(ServiceType serviceType) {
        return list("serviceType = ?1 and status = ?2", serviceType, Status.ACTIVE);
    }

    @Override
    @Transactional
    @CacheInvalidateAll(cacheName = "active-tariffs")
    public Tariff updateTariff(Tariff tariff) {
        tariff.update();
        return tariff;
    }

    @Override
    @Transactional
    @CacheInvalidateAll(cacheName = "active-tariffs")
    public boolean deleteTariff(String tariffId) {
        return delete("_id", tariffId) > 0;
    }

    private void ensureRateIdentifiers(Tariff tariff) {
        if (tariff == null || tariff.rates == null) return;
        boolean updated = false;
        for (RateManagement rate : tariff.rates) {
            if (rate == null) continue;
            String before = rate.getRateId();
            rate.ensureRateId();
            if (before == null || before.isBlank()) updated = true;
        }
        if (updated) tariff.update();
    }
}
