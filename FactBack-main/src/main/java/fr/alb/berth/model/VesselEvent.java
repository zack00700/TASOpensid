package fr.alb.berth.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.Instant;

@MongoEntity(collection = "VESSEL_EVENT")
public class VesselEvent extends EntityBase {

    private static final long serialVersionUID = 1L;

    public String visitId;
    public String eventId;
    public Instant eventDate;
    public String notes;
}
