package fr.alb.startup;

import fr.alb.dd.model.DdRule;
import fr.alb.equipment.api.IsoContainerCodeRegistry;
import fr.alb.yard.model.Item;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One-shot migration of legacy container-type values (e.g. "20DC", "40HC") to
 * ISO 6346 codes ("22G1", "45G1") on Item.containerType and DdRule.containerTypeCode.
 *
 * <p>Lives in {@code fr.alb.startup} (a non-context, cross-cutting package
 * exempt from {@code BoundedContextArchitectureTest}) because the migrator
 * inherently spans three contexts: it reads from {@code equipment} (via the
 * api registry) and mutates entities in {@code yard} and {@code dd}.
 *
 * <p>Runs at @Priority(APPLICATION + 2000) — AFTER IsoContainerCodeSeeder
 * (priority APPLICATION + 1000) so the registry is populated before lookups.
 *
 * <p>Idempotent: values already in the registry are skipped on every reboot.
 * Unknown values are left as-is and will fail validation on the next edit.
 */
@ApplicationScoped
public class IsoContainerTypeMigrator {

    private static final Logger LOGGER = Logger.getLogger(IsoContainerTypeMigrator.class);

    @Inject
    IsoContainerCodeRegistry registry;

    /** Default mapping from observed legacy values to ISO 6346 codes. */
    public static final Map<String, String> LEGACY_TO_ISO = Map.ofEntries(
            // Dry / GP — modern fleet ~95% in 8'6" → G1 with vents
            Map.entry("20DC",      "22G1"),
            Map.entry("20FT",      "22G1"),
            Map.entry("40DC",      "42G1"),
            Map.entry("40FT",      "42G1"),
            Map.entry("40HC",      "45G1"),
            Map.entry("45HC",      "L5G1"),
            // Reefer
            Map.entry("20RF",      "22R1"),
            Map.entry("REEFER_20", "22R1"),
            Map.entry("40RF",      "42R1"),
            Map.entry("REEFER_40", "42R1"),
            // Tank — default non-haz
            Map.entry("TANK",      "22T0"),
            // Flat Rack — default 40' fixed ends
            Map.entry("FLAT",      "42P1"),
            Map.entry("FLAT_RACK", "42P1"),
            // Open Top — default 40'
            Map.entry("OPEN_TOP",  "42U1")
    );

    /**
     * No {@code @Transactional}: Cosmos DB (Mongo API) does not support cross-collection
     * transactions, and this migrator iterates two collections (ITEM + DD_RULE).
     * Each {@code update()} is its own write; the predicate-based idempotence guarantees
     * that a partial run resumes correctly on the next reboot.
     */
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 2000) StartupEvent ev) {
        Predicate<String> isInRegistry = registry::contains;
        int items = migrateItems(isInRegistry);
        int rules = migrateDdRules(isInRegistry);
        if (items + rules > 0) {
            LOGGER.infof("IsoContainerTypeMigrator: migrated %d Items + %d DdRules", items, rules);
        } else {
            LOGGER.debug("IsoContainerTypeMigrator: no legacy values found, skipping");
        }
    }

    /** Pure helper — visible for unit tests. Idempotent. Unknown values left as-is. */
    static String mapLegacyToIso(String legacy, Predicate<String> isInRegistry) {
        if (legacy == null || legacy.isBlank()) return legacy;
        // If value already exists in the registry, leave as-is (idempotent across reboots).
        if (isInRegistry.test(legacy)) return legacy;
        return LEGACY_TO_ISO.getOrDefault(legacy, legacy);
    }

    private int migrateItems(Predicate<String> isInRegistry) {
        int count = 0;
        List<Item> all = Item.<Item>findAll().list();
        for (Item it : all) {
            String old = it.getContainerType();
            String mapped = mapLegacyToIso(old, isInRegistry);
            if (mapped != null && !mapped.equals(old)) {
                it.setContainerType(mapped);
                it.update();
                LOGGER.debugf("IsoContainerTypeMigrator: Item %s containerType %s -> %s", it.getId(), old, mapped);
                count++;
            }
        }
        return count;
    }

    private int migrateDdRules(Predicate<String> isInRegistry) {
        int count = 0;
        List<DdRule> all = DdRule.<DdRule>findAll().list();
        for (DdRule r : all) {
            String old = r.containerTypeCode;
            String mapped = mapLegacyToIso(old, isInRegistry);
            if (mapped != null && !mapped.equals(old)) {
                r.containerTypeCode = mapped;
                r.update();
                LOGGER.debugf("IsoContainerTypeMigrator: DdRule %s containerTypeCode %s -> %s", r.getId(), old, mapped);
                count++;
            }
        }
        return count;
    }
}
