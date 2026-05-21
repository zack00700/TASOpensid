package fr.alb.yard.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import fr.alb.dd.DdAccrualService;
import fr.alb.dto.ErrorResponse;
import fr.alb.yard.model.EventConfig;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import fr.alb.type.EventType;
import fr.alb.type.LifeCycleStatus;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("item")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ItemEventResource {

    @Inject
    DdAccrualService ddAccrualService;

    public static class ItemEventRequest {
        public String eventId;
        public Instant eventDate;
        /**
         * Deprecated: phase is derived from the associated EventConfig's eventType.
         * If provided, it will be ignored.
         */
        @Deprecated
        public EventType phase;
    }

    public static class LifecycleResponse {
        public String lifecycleId;
        public LifeCycleStatus status;
        public Instant startTime;
        public Instant endTime;
        public List<String> eventIds;
    }

    @POST
    @Path("/{itemId}/event")
    public Response createEvent(@PathParam("itemId") String itemId, ItemEventRequest req) {
        if (req == null || req.eventId == null || req.eventDate == null) {
            throw new BadRequestException();
        }
        EventConfig cfg = EventConfig.findById(req.eventId);
        if (cfg == null) {
            return Response.status(BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "Unknown eventId", 400))
                    .build();
        }
        if (cfg.getEventType() == null) {
            return Response.status(BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "eventConfig.eventType is required", 400))
                    .build();
        }
        if (req.phase != null) {
            Log.warn("'phase' field is deprecated and will be ignored. Use eventConfig.eventType instead.");
        }
        ItemEvent evt = new ItemEvent();
        evt.setItemId(itemId);
        evt.setEventId(req.eventId);
        evt.setEventDate(req.eventDate);

        Lifecycle active = Lifecycle.find("itemId = ?1 and status = ?2", itemId, LifeCycleStatus.IN_PROGRESS).firstResult();

        EventType type = cfg.getEventType();
        switch (type) {
            case IN:
                if (active != null) {
                    return Response.status(Response.Status.CONFLICT)
                            .entity(new ErrorResponse("CONFLICT", "Lifecycle already active", 409))
                            .build();
                }
                evt.persist();
                Lifecycle lc = new Lifecycle();
                lc.setItemId(itemId);
                lc.setStartTime(req.eventDate);
                lc.setStatus(LifeCycleStatus.IN_PROGRESS);
                lc.getEventIds().add(evt.getId());
                lc.persist();
                Log.debugf("[Lifecycle] Created lifecycle=%s for item=%s", lc.getId(), itemId);
                Item item = Item.findById(itemId);
                if (item != null && !item.getLifeCycles().contains(lc.getId())) {
                    item.getLifeCycles().add(lc.getId());
                    item.update();
                    Log.debugf("[Item] Updated item=%s with lifecycle reference=%s", itemId, lc.getId());
                }
                // D&D hook: start DEMURRAGE accrual on GATE_IN (EventType.IN).
                // carrierId is sourced from item.inboundVoyage as a best-effort fallback;
                // pass null when not available and the resolver will pick the default rule.
                if (item != null) {
                    try {
                        String carrierId = item.getInboundVoyage();
                        ddAccrualService.onGateIn(item, carrierId);
                    } catch (Exception e) {
                        Log.warnf("DD accrual hook failed for item %s: %s", itemId, e.getMessage());
                    }
                }
                LifecycleResponse resp = toResponse(lc);
                return Response.status(Response.Status.CREATED).entity(resp).build();
            case INTERMEDIATE:
                if (active == null) {
                    return Response.status(BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "No active lifecycle", 400))
                            .build();
                }
                if (!isChronologicallyValid(active, req.eventDate)) {
                    return Response.status(BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "Invalid eventDate", 400))
                            .build();
                }
                evt.persist();
                active.getEventIds().add(evt.getId());
                active.update();
                Log.debugf("[Lifecycle] Updated lifecycle=%s for item=%s with event=%s", active.getId(), itemId, evt.getId());
                return Response.status(Response.Status.CREATED).entity(toResponse(active)).build();
            case OUT:
                if (active == null) {
                    return Response.status(BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "No active lifecycle", 400))
                            .build();
                }
                if (!isChronologicallyValid(active, req.eventDate) || req.eventDate.isBefore(active.getStartTime())) {
                    return Response.status(BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "Invalid eventDate", 400))
                            .build();
                }
                evt.persist();
                active.getEventIds().add(evt.getId());
                active.setStatus(LifeCycleStatus.COMPLETED);
                active.setEndTime(req.eventDate);
                active.update();
                Log.debugf("[Lifecycle] Updated lifecycle=%s for item=%s with event=%s; endTime=%s", active.getId(), itemId, evt.getId(), req.eventDate);
                // D&D hook: stop DEMURRAGE and start DETENTION accrual on GATE_OUT (EventType.OUT).
                // carrierId is sourced from item.outboundVoyage as a best-effort fallback.
                Item outItem = Item.findById(itemId);
                if (outItem != null) {
                    try {
                        String carrierId = outItem.getOutboundVoyage();
                        ddAccrualService.onGateOut(outItem, carrierId);
                    } catch (Exception e) {
                        Log.warnf("DD accrual hook failed for item %s: %s", itemId, e.getMessage());
                    }
                }
                return Response.status(Response.Status.CREATED).entity(toResponse(active)).build();
            default:
                throw new BadRequestException();
        }
    }

    private boolean isChronologicallyValid(Lifecycle lc, Instant newDate) {
        if (newDate.isBefore(lc.getStartTime())) {
            return false;
        }
        if (lc.getEventIds().isEmpty()) {
            return true;
        }
        ItemEvent last = ItemEvent.findById(lc.getEventIds().get(lc.getEventIds().size() - 1));
        return last == null || !newDate.isBefore(last.getEventDate());
    }

    @GET
    @Path("/{itemId}/lifecycles")
    public List<LifecycleResponse> getLifecycles(@PathParam("itemId") String itemId) {
        List<Lifecycle> lcs = Lifecycle.find("itemId", itemId).list();
        List<LifecycleResponse> out = new ArrayList<>();
        for (Lifecycle lc : lcs) {
            out.add(toResponse(lc));
        }
        return out;
    }

    private LifecycleResponse toResponse(Lifecycle lc) {
        LifecycleResponse resp = new LifecycleResponse();
        resp.lifecycleId = lc.getId();
        resp.status = lc.getStatus();
        resp.startTime = lc.getStartTime();
        resp.endTime = lc.getEndTime();
        resp.eventIds = lc.getEventIds();
        return resp;
    }
}
