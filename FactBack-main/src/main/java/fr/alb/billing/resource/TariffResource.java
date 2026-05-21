package fr.alb.billing.resource;

import java.util.List;

import fr.alb.billing.dao.TariffDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.billing.model.Tariff;
import fr.alb.type.ServiceType;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * CRUD endpoints for Tariff — the master rate schedule entity.
 *
 * Tariffs define what we charge (rates + calculation mode) for a given service type.
 * Contracts link customers to a Tariff.
 */
@Path("tariffs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TariffResource {

    @Inject
    TariffDao tariffDao;

    @POST
    @RolesAllowed("ROLE_ADMIN")
    public Response createTariff(Tariff tariff) {
        if (tariff == null || tariff.name == null || tariff.name.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "tariff.name is required", 400))
                    .build();
        }
        try {
            tariffDao.addTariff(tariff);
            return Response.status(201).entity(tariff).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getTariffs(@QueryParam("serviceType") String serviceType) {
        try {
            if (serviceType != null && !serviceType.isBlank()) {
                ServiceType st = ServiceType.fromValue(serviceType);
                return Response.ok(tariffDao.findByServiceType(st)).build();
            }
            return Response.ok(tariffDao.getActiveTariffs()).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @Path("{tariffId}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getTariff(@PathParam("tariffId") String tariffId) {
        try {
            Tariff tariff = tariffDao.findTariff(tariffId);
            if (tariff == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Tariff not found: " + tariffId, 404))
                        .build();
            }
            return Response.ok(tariff).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @PUT
    @Path("{tariffId}")
    @RolesAllowed("ROLE_ADMIN")
    public Response updateTariff(@PathParam("tariffId") String tariffId, Tariff incoming) {
        try {
            Tariff existing = tariffDao.findTariff(tariffId);
            if (existing == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Tariff not found: " + tariffId, 404))
                        .build();
            }
            incoming.setId(existing.getId());
            tariffDao.updateTariff(incoming);
            return Response.ok(incoming).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @DELETE
    @Path("{tariffId}")
    @RolesAllowed("ROLE_ADMIN")
    public Response deleteTariff(@PathParam("tariffId") String tariffId) {
        try {
            boolean deleted = tariffDao.deleteTariff(tariffId);
            if (deleted) return Response.noContent().build();
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "Tariff not found: " + tariffId, 404))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
