package fr.alb.yard.model;

import fr.alb.model.EntityBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.type.LifeCycleStatus;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "LIFECYCLE")
public class Lifecycle extends EntityBase {
    private static final long serialVersionUID = 1L;

    private String itemId;
    private Instant startTime;
    private Instant endTime;
    private LifeCycleStatus status;
    private List<String> eventIds = new ArrayList<>();

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public LifeCycleStatus getStatus() {
        return status;
    }

    public void setStatus(LifeCycleStatus status) {
        this.status = status;
    }

    public List<String> getEventIds() {
        return eventIds;
    }

    public void setEventIds(List<String> eventIds) {
        this.eventIds = eventIds;
    }
}
