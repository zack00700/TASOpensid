package fr.alb.yard.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.yard.model.EventConfig;
import fr.alb.yard.model.ItemEvent;
import fr.alb.type.EventType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.annotation.JsonInclude;

@Path("item-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ItemEventLookupResource {

    public static class LookupRequest {
        public List<String> ids;
    }

    public static class EventInfo {
        public String id;
        public Instant eventDate;
        public String eventName;
        public EventType eventType;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class LookupResult {
        public List<EventInfo> events = new ArrayList<>();
        public List<String> missing;
    }

    @POST
    @Path("/lookup")
    public LookupResult lookup(LookupRequest req) {
        LookupResult result = new LookupResult();
        if (req == null || req.ids == null) {
            return result;
        }
        for (String id : req.ids) {
            ItemEvent evt = ItemEvent.findById(id);
            if (evt != null) {
                EventConfig cfg = EventConfig.findById(evt.getEventId());
                if (cfg != null) {
                    EventInfo info = new EventInfo();
                    info.id = evt.getId();
                    info.eventDate = evt.getEventDate();
                    info.eventName = cfg.getEventName();
                    info.eventType = cfg.getEventType();
                    result.events.add(info);
                } else {
                    if (result.missing == null) result.missing = new ArrayList<>();
                    result.missing.add(id);
                }
            } else {
                if (result.missing == null) result.missing = new ArrayList<>();
                result.missing.add(id);
            }
        }
        return result;
    }
}
