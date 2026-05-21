package fr.alb.ais.resource;

import fr.alb.ais.client.AisStreamClient;
import fr.alb.ais.model.VesselAisSnapshot;
import fr.alb.ais.service.AisVisitMatcher;
import fr.alb.berth.model.Vessel;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/ais")
@Produces(MediaType.APPLICATION_JSON)
public class VesselAisResource {

    @Inject
    AisStreamClient client;

    @Inject
    AisVisitMatcher matcher;

    /** Operational health of the AIS pipeline. */
    @GET
    @Path("/health")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", client.isConnected());
        Instant last = client.lastMessageAt();
        body.put("lastMessageAt", last == null ? null : last.toString());
        long count = VesselAisSnapshot.count();
        body.put("snapshotCount", count);
        return Response.ok(body).build();
    }

    /** Lookup a snapshot by MMSI directly. */
    @GET
    @Path("/by-mmsi/{mmsi}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response byMmsi(@PathParam("mmsi") String mmsi) {
        VesselAisSnapshot s = VesselAisSnapshot.<VesselAisSnapshot>find("mmsi", mmsi).firstResult();
        if (s == null) return Response.status(404).build();
        return Response.ok(s).build();
    }

    /** Resolve a snapshot via Vessel ID → Vessel.mmsi. Returns 204 when no snapshot is available. */
    @GET
    @Path("/by-vessel/{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response byVesselId(@PathParam("id") String id) {
        Vessel v = Vessel.findById(id);
        if (v == null) return Response.status(404).build();
        if (v.mmsi == null || v.mmsi.isBlank()) return Response.noContent().build();
        VesselAisSnapshot s = VesselAisSnapshot.<VesselAisSnapshot>find("mmsi", v.mmsi).firstResult();
        if (s == null) return Response.noContent().build();
        return Response.ok(s).build();
    }

    /** Suggestions derived from a Visit's vessel snapshot.
     *  204 when visit not found, vessel has no MMSI, no snapshot, or snapshot has no useful fields. */
    @GET
    @Path("/by-visit/{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response byVisitId(@PathParam("id") String id) {
        return matcher.findSuggestionsFor(id)
            .map(dto -> Response.ok(dto).build())
            .orElseGet(() -> Response.noContent().build());
    }
}
