package fr.alb.billing.dao;

import java.util.List;
import java.util.Optional;

import fr.alb.billing.model.Contract;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.model.Tariff;
import fr.alb.type.Status;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ContractDaoImpl implements PanacheMongoRepository<Contract>, ContractDao {

    @Override
    @Transactional
    @CacheInvalidateAll(cacheName = "active-contracts")
    public void addContract(Contract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("Contract cannot be null");
        }

        Optional<Contract> existingContract = find("name", contract.name).firstResultOptional();
        if (existingContract.isPresent()) {
            throw new IllegalArgumentException("Contract " + contract.name + " already exists");
        }

        persist(contract);
    }

    /**
     * Gets all contracts.
     * Read-only operation - no transaction needed as it only queries and ensures rate identifiers.
     * Note: ensureRateIdentifiers may update contracts if rate IDs are missing, but this is done
     * on each contract individually with its own transaction.
     */
    @Override
    @Transactional
    public List<Contract> getContracts() {
        List<Contract> contracts = listAll();
        contracts.forEach(this::ensureRateIdentifiers);
        return contracts;
    }

    /**
     * Finds a contract by ID.
     * Mirrors the read pipeline of findActiveContracts: ensures rate IDs exist
     * (may write back if a legacy rate has no rateId) and hydrates the linked
     * Tariff's rates/calculationMode when the contract references one without
     * embedded rates.
     */
    @Override
    @Transactional
    public Contract findContract(String contractId) {
        if (contractId == null) {
            throw new IllegalArgumentException("contractId cannot be null or blank");
        }

        if (contractId.trim().isEmpty()) {
            throw new IllegalArgumentException("contractId cannot be null or blank");
        }

        Contract contract = find("_id", contractId.trim()).firstResult();
        if (contract == null) return null;
        ensureRateIdentifiers(contract);
        hydrateFromTariff(contract);
        return contract;
    }

    /**
     * Finds active contracts.
     * Read-only operation - no transaction needed as it only queries and ensures rate identifiers.
     * Note: ensureRateIdentifiers may update contracts if rate IDs are missing, but this is done
     * on each contract individually with its own transaction.
     */
    @Transactional
    @CacheResult(cacheName = "active-contracts")
    public List<Contract> findActiveContracts() {
        List<Contract> contracts = list("status", Status.ACTIVE);
        contracts.forEach(this::ensureRateIdentifiers);
        contracts.forEach(this::hydrateFromTariff);
        return contracts;
    }

    /**
     * When a Contract references a Tariff via tariffId, load the Tariff's
     * rates and calculationMode into the Contract in-memory (not persisted).
     * This allows the entire billing pipeline to work unchanged while supporting
     * the Tariff→Contract separation model.
     *
     * Hydration is skipped when the Contract already has its own embedded rates,
     * so backward compatibility is preserved for existing data.
     */
    private void hydrateFromTariff(Contract contract) {
        if (contract == null || contract.tariffId == null || contract.tariffId.isBlank()) return;
        if (contract.rates != null && !contract.rates.isEmpty()) return; // own rates take priority
        Tariff tariff = Tariff.findById(contract.tariffId);
        if (tariff == null) return;
        contract.rates = tariff.rates;
        contract.calculationMode = tariff.calculationMode;
    }

    @Override
    @Transactional
    @CacheInvalidateAll(cacheName = "active-contracts")
    public Contract updateContract(Contract contract) {
        contract.update();
        return contract;
    }

    /**
     * Ensures every rate inside the contract has a stable UUID identifier.
     *
     * No N+1 read-query issue here:
     *   - RateManagement.ensureRateId() is a pure in-memory operation — it
     *     generates a UUID via UUID.randomUUID() and assigns it to the field;
     *     it makes no DB calls.  // Pure in-memory computation — no DB query
     *   - contract.update() is called at most once per contract, and only when at
     *     least one rate was missing its ID (a one-time data-migration write).
     *     For contracts where all rates already carry IDs this method is a no-op.
     */
    private void ensureRateIdentifiers(Contract contract) {
        if (contract == null || contract.rates == null) {
            return;
        }
        boolean updated = false;
        for (RateManagement rate : contract.rates) {
            if (rate == null) {
                continue;
            }
            String before = rate.getRateId();
            String after = rate.ensureRateId();
            if (before == null || before.isBlank()) {
                updated = true;
            }
        }
        if (updated) {
            contract.update();
        }
    }
}
