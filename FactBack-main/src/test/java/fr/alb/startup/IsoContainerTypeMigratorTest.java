package fr.alb.startup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class IsoContainerTypeMigratorTest {

    /** Predicate built from the actual seed file so the exhaustive-coverage test is grounded. */
    private static final Predicate<String> SEED_REGISTRY = buildSeedPredicate();

    private static Predicate<String> buildSeedPredicate() {
        try (InputStream in = IsoContainerTypeMigratorTest.class.getResourceAsStream("/seed/iso-container-codes.json")) {
            if (in == null) throw new IllegalStateException("Seed file not found on classpath");
            List<Map<String, Object>> seeds = new ObjectMapper().readValue(in, new TypeReference<>() {});
            Set<String> codes = new HashSet<>();
            for (Map<String, Object> s : seeds) codes.add((String) s.get("code"));
            return codes::contains;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsLegacyDryContainerToIsoGp() {
        assertEquals("22G1", IsoContainerTypeMigrator.mapLegacyToIso("20DC", SEED_REGISTRY));
    }

    @Test
    void idempotentOnAlreadyIsoValue() {
        assertEquals("22G1", IsoContainerTypeMigrator.mapLegacyToIso("22G1", SEED_REGISTRY));
    }

    @Test
    void nullPassesThrough() {
        assertNull(IsoContainerTypeMigrator.mapLegacyToIso(null, SEED_REGISTRY));
    }

    @Test
    void blankPassesThrough() {
        assertEquals("   ", IsoContainerTypeMigrator.mapLegacyToIso("   ", SEED_REGISTRY));
    }

    @Test
    void unknownValueLeftAsIs() {
        assertEquals("UNKNOWN_GARBAGE",
                IsoContainerTypeMigrator.mapLegacyToIso("UNKNOWN_GARBAGE", SEED_REGISTRY));
    }

    @Test
    void everyMappingTargetExistsInSeed() {
        for (Map.Entry<String, String> entry : IsoContainerTypeMigrator.LEGACY_TO_ISO.entrySet()) {
            String legacy = entry.getKey();
            String iso = entry.getValue();
            assertTrue(SEED_REGISTRY.test(iso),
                    "Mapping " + legacy + " → " + iso + " targets a code not present in the seed file");
        }
    }
}
