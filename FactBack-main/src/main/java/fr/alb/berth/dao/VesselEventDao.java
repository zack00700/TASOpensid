package fr.alb.berth.dao;

import fr.alb.berth.model.VesselEvent;

import java.util.List;

public interface VesselEventDao {
    void addVesselEvent(VesselEvent event);
    List<VesselEvent> findByVisitId(String visitId);
}
