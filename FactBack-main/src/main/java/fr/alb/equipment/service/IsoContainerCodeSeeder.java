package fr.alb.equipment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.equipment.model.IsoContainerCode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Seeds the ISO_CONTAINER_CODE collection on first boot with ~30 curated
 * ISO 6346 codes from /seed/iso-container-codes.json. Idempotent — re-runs
 * after data exists are no-ops.
 */
@ApplicationScoped
public class IsoContainerCodeSeeder {

    private static final Logger LOGGER = Logger.getLogger(IsoContainerCodeSeeder.class);
    private static final String SEED_PATH = "/seed/iso-container-codes.json";

    // Higher priority value = later execution. Must run AFTER IndexInitializer
    // (priority APPLICATION) because Cosmos DB refuses to create a unique index
    // on a non-empty collection.
    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION + 1000) StartupEvent ev) {
        if (IsoContainerCode.count() > 0) {
            LOGGER.debug("IsoContainerCodeSeeder: skipping (collection non-empty)");
            return;
        }
        try {
            List<IsoContainerCodeSeed> seeds = readSeedFile();
            for (IsoContainerCodeSeed s : seeds) {
                IsoContainerCode entity = mapToEntity(s);
                entity.persist();
            }
            LOGGER.infof("IsoContainerCodeSeeder: seeded %d codes from %s", seeds.size(), SEED_PATH);
        } catch (Exception e) {
            LOGGER.errorf(e, "IsoContainerCodeSeeder: failed to load seed; collection left empty");
        }
    }

    /** Pure helper — visible for unit tests. */
    static IsoContainerCode mapToEntity(IsoContainerCodeSeed s) {
        IsoContainerCode e = new IsoContainerCode();
        e.code = s.code;
        e.description = s.description;
        e.lengthFt = s.lengthFt;
        e.heightFt = s.heightFt;
        e.typeGroup = s.typeGroup;
        e.isReefer = s.isReefer;
        e.isHazmatCapable = s.isHazmatCapable;
        e.isTank = s.isTank;
        e.isOpenTop = s.isOpenTop;
        e.isStandard = true;
        e.isActive = true;
        e.archetypeId = null;
        e.tareKg = s.tareKg;
        e.maxPayloadKg = s.maxPayloadKg;
        e.maxGrossKg = s.maxGrossKg;
        return e;
    }

    private List<IsoContainerCodeSeed> readSeedFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(SEED_PATH)) {
            if (in == null) throw new IOException("Seed file not found: " + SEED_PATH);
            return new ObjectMapper().readValue(in, new TypeReference<List<IsoContainerCodeSeed>>() {});
        }
    }

    /** Plain DTO for Jackson deserialization. Public so the test can construct fixtures. */
    public static class IsoContainerCodeSeed {
        public String code;
        public String description;
        public Integer lengthFt;
        public Double heightFt;
        public String typeGroup;
        public boolean isReefer;
        public boolean isHazmatCapable;
        public boolean isTank;
        public boolean isOpenTop;
        public Integer tareKg;
        public Integer maxPayloadKg;
        public Integer maxGrossKg;
    }
}
