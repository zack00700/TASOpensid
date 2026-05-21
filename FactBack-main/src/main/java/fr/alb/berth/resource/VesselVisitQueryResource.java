package fr.alb.berth.resource;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import fr.alb.dto.VesselVisitDTO;
import fr.alb.dto.VesselVisitSearchDTO;
import fr.alb.berth.model.Vessel;
import fr.alb.berth.model.Visit;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Custom endpoints for searching and retrieving vessel visits. This is used by
 * the Bill of Lading transport information flow.
 */
@Path("vessel-visits")
@Produces(MediaType.APPLICATION_JSON)
public class VesselVisitQueryResource {

    @GET
    @Path("search")
    public List<VesselVisitSearchDTO> search(@QueryParam("q") String query,
            @QueryParam("limit") @DefaultValue("10") int limit) {
        String term = (query == null || query.isBlank()) ? "" : query;
        String regex = "(?i).*" + term + ".*";

        List<Visit> visits = Visit
                .find("{'$or': [ {'vesselName': {'$regex': ?1}}, {'inboundVoyage': {'$regex': ?1}}, {'outboundVoyage': {'$regex': ?1}}, {'id': {'$regex': ?1}} ]}",
                        regex)
                .page(0, limit).list();

        List<VesselVisitSearchDTO> results = new ArrayList<>();
        for (Visit visit : visits) {
            VesselVisitSearchDTO dto = new VesselVisitSearchDTO();
            dto.id = visit.getId();
            dto.vesselName = visit.vesselName;
            Vessel vessel = visit.vesselId != null ? Vessel.findById(visit.vesselId) : null;
            dto.imo = vessel != null ? vessel.imoNumber : null;
            dto.voyageIn = visit.inboundVoyage;
            dto.voyageOut = visit.outboundVoyage;
            dto.terminal = visit.facility;
            dto.eta = visit.eta != null ? visit.eta.toInstant(ZoneOffset.UTC) : null;
            dto.etd = visit.etd != null ? visit.etd.toInstant(ZoneOffset.UTC) : null;
            results.add(dto);
        }
        return results;
    }

    @GET
    @Path("{id}")
    public Response get(@PathParam("id") String id) {
        Visit visit = Visit.findById(id);
        if (visit == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Vessel vessel = visit.vesselId != null ? Vessel.findById(visit.vesselId) : null;
        VesselVisitDTO dto = new VesselVisitDTO();
        dto.id = visit.getId();
        dto.vesselName = visit.vesselName;
        dto.imo = vessel != null ? vessel.imoNumber : null;
        dto.callSign = vessel != null ? vessel.callSign : null;
        dto.voyageIn = visit.inboundVoyage;
        dto.voyageOut = visit.outboundVoyage;
        dto.operator = visit.lineOperator != null ? visit.lineOperator : (vessel != null ? vessel.operator : null);
        dto.port = visit.pol;
        dto.terminal = visit.facility;
        dto.berth = null;
        dto.eta = visit.eta != null ? visit.eta.toInstant(ZoneOffset.UTC) : null;
        dto.etd = visit.etd != null ? visit.etd.toInstant(ZoneOffset.UTC) : null;
        dto.ata = visit.ata != null ? visit.ata.toInstant(ZoneOffset.UTC) : null;
        dto.atd = visit.atd != null ? visit.atd.toInstant(ZoneOffset.UTC) : null;
        return Response.ok(dto).build();
    }
}

