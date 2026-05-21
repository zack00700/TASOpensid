package fr.alb.berth.dao;

import fr.alb.berth.model.VesselEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class VesselEventDaoImplTest {

    @Inject
    VesselEventDaoImpl dao;

    @BeforeEach
    void clean() {
        VesselEvent.deleteAll();
    }

    @Test
    void addVesselEvent_persistsAndAssignsId() {
        VesselEvent e = new VesselEvent();
        e.visitId = "visit-1";
        e.eventId = "evt-cfg-1";
        e.eventDate = Instant.parse("2026-05-14T14:30:00Z");
        e.notes = "Pilot embarked";

        dao.addVesselEvent(e);

        assertNotNull(e.getId());
        VesselEvent found = VesselEvent.findById(e.getId());
        assertNotNull(found);
        assertEquals("Pilot embarked", found.notes);
    }

    @Test
    void findByVisitId_returnsEventsForOnlyThatVisit_sortedByDateDesc() {
        VesselEvent older = new VesselEvent();
        older.visitId = "visit-1";
        older.eventId = "evt-cfg-1";
        older.eventDate = Instant.parse("2026-05-14T08:00:00Z");
        older.notes = "Older";
        dao.addVesselEvent(older);

        VesselEvent newer = new VesselEvent();
        newer.visitId = "visit-1";
        newer.eventId = "evt-cfg-1";
        newer.eventDate = Instant.parse("2026-05-14T14:30:00Z");
        newer.notes = "Newer";
        dao.addVesselEvent(newer);

        VesselEvent otherVisit = new VesselEvent();
        otherVisit.visitId = "visit-2";
        otherVisit.eventId = "evt-cfg-1";
        otherVisit.eventDate = Instant.parse("2026-05-14T10:00:00Z");
        otherVisit.notes = "Other visit";
        dao.addVesselEvent(otherVisit);

        List<VesselEvent> events = dao.findByVisitId("visit-1");
        assertEquals(2, events.size());
        assertEquals("Newer", events.get(0).notes);
        assertEquals("Older", events.get(1).notes);
    }
}
