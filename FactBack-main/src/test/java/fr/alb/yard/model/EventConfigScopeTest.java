package fr.alb.yard.model;

import fr.alb.type.EventScope;
import fr.alb.type.EventType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EventConfigScopeTest {

    @BeforeEach
    void clean() {
        EventConfig.deleteAll();
    }

    @Test
    void scopeFieldPersistsRoundTrip() {
        EventConfig cfg = new EventConfig("Pilot Boarded", EventType.INTERMEDIATE, false);
        cfg.setScope(EventScope.VESSEL);
        cfg.persist();

        EventConfig found = EventConfig.findById(cfg.getId());
        assertNotNull(found);
        assertEquals(EventScope.VESSEL, found.getScope());
    }

    @Test
    void legacyDocumentMissingScopeDefaultsToITEM() {
        EventConfig cfg = new EventConfig("Gate-In", EventType.IN, true);
        cfg.persist();

        EventConfig found = EventConfig.findById(cfg.getId());
        assertNotNull(found);
        assertEquals(EventScope.ITEM, found.getScope());
    }
}
