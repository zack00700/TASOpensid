package fr.alb.equipment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.equipment.model.IsoContainerCode;
import fr.alb.equipment.service.IsoContainerCodeSeeder.IsoContainerCodeSeed;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IsoContainerCodeSeederTest {

    @Test
    void mapToEntityForcesIsStandardTrueAndIsActiveTrueAndArchetypeIdNull() {
        IsoContainerCodeSeed seed = new IsoContainerCodeSeed();
        seed.code = "22G1";
        seed.description = "20' GP";
        seed.lengthFt = 20;
        seed.heightFt = 8.5;
        seed.typeGroup = "G";
        seed.isReefer = false;
        seed.isHazmatCapable = false;
        seed.isTank = false;
        seed.isOpenTop = false;
        seed.tareKg = 2300;
        seed.maxPayloadKg = 28180;
        seed.maxGrossKg = 30480;

        IsoContainerCode entity = IsoContainerCodeSeeder.mapToEntity(seed);

        assertEquals("22G1", entity.code);
        assertEquals("20' GP", entity.description);
        assertEquals(20, entity.lengthFt);
        assertEquals(8.5, entity.heightFt);
        assertEquals("G", entity.typeGroup);
        assertTrue(entity.isStandard, "isStandard must be true for seeded entities");
        assertTrue(entity.isActive, "isActive defaults to true");
        assertNull(entity.archetypeId, "archetypeId must be null on seed");
        assertEquals(2300, entity.tareKg);
        assertEquals(28180, entity.maxPayloadKg);
        assertEquals(30480, entity.maxGrossKg);
    }

    @Test
    void mapToEntityFlowsAllMetierFlagsExplicitly() {
        IsoContainerCodeSeed seed = new IsoContainerCodeSeed();
        seed.code = "22T6";
        seed.typeGroup = "T";
        seed.isReefer = false;
        seed.isHazmatCapable = true;
        seed.isTank = true;
        seed.isOpenTop = false;

        IsoContainerCode entity = IsoContainerCodeSeeder.mapToEntity(seed);

        assertFalse(entity.isReefer);
        assertTrue(entity.isHazmatCapable);
        assertTrue(entity.isTank);
        assertFalse(entity.isOpenTop);
    }

    @Test
    void mapToEntityHandlesNullWeightFields() {
        IsoContainerCodeSeed seed = new IsoContainerCodeSeed();
        seed.code = "L5G1";
        seed.tareKg = null;
        seed.maxPayloadKg = null;
        seed.maxGrossKg = null;

        IsoContainerCode entity = IsoContainerCodeSeeder.mapToEntity(seed);

        assertNull(entity.tareKg);
        assertNull(entity.maxPayloadKg);
        assertNull(entity.maxGrossKg);
    }

    @Test
    void seedJsonFileParsesIntoExactly30Entries() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/seed/iso-container-codes.json")) {
            assertNotNull(in, "Seed file must be on the classpath at /seed/iso-container-codes.json");
            List<IsoContainerCodeSeed> seeds = new ObjectMapper().readValue(
                in, new TypeReference<List<IsoContainerCodeSeed>>() {});
            assertEquals(30, seeds.size(), "Seed must contain exactly 30 entries");
            // Spot-check a known-anchor entry.
            IsoContainerCodeSeed sample = seeds.stream().filter(s -> "22G1".equals(s.code)).findFirst().orElse(null);
            assertNotNull(sample, "Seed must include 22G1");
            assertEquals(20, sample.lengthFt);
            assertEquals(8.5, sample.heightFt);
            assertEquals("G", sample.typeGroup);
        }
    }
}
