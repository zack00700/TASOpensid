package fr.alb.berth.dao;

import fr.alb.berth.model.VesselEvent;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class VesselEventDaoImpl implements VesselEventDao {

    @Override
    @Transactional
    public void addVesselEvent(VesselEvent event) {
        if (event == null) throw new IllegalArgumentException("VesselEvent cannot be null");
        if (event.visitId == null || event.visitId.isBlank()) {
            throw new IllegalArgumentException("visitId is required");
        }
        if (event.eventId == null || event.eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (event.eventDate == null) {
            throw new IllegalArgumentException("eventDate is required");
        }
        if (event.notes == null || event.notes.isBlank()) {
            throw new IllegalArgumentException("notes is required");
        }
        if (event.notes.length() > 500) {
            throw new IllegalArgumentException("notes max 500 chars");
        }
        event.persist();
    }

    @Override
    public List<VesselEvent> findByVisitId(String visitId) {
        return VesselEvent.find("visitId", Sort.descending("eventDate"), visitId).list();
    }
}
