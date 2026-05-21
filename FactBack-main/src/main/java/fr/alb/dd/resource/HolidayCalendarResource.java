package fr.alb.dd.resource;

import java.util.List;

import fr.alb.dto.ErrorResponse;
import fr.alb.dd.model.HolidayCalendar;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
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
 * REST resource for port/country holiday calendars.
 *
 * Holiday calendars are used by the FreeDayCalculator to exclude non-working
 * days from D&D free-day consumption.
 */
@Path("dd/holidays")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class HolidayCalendarResource {

    /**
     * List holiday calendars, optionally filtered by year and/or portCode.
     */
    @GET
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response listCalendars(
            @QueryParam("year") Integer year,
            @QueryParam("portCode") String portCode) {
        try {
            List<HolidayCalendar> calendars;
            boolean hasYear = year != null;
            boolean hasPort = portCode != null && !portCode.isBlank();

            if (hasYear && hasPort) {
                calendars = HolidayCalendar.find("year = ?1 and portCode = ?2", year, portCode).list();
            } else if (hasYear) {
                calendars = HolidayCalendar.find("year", year).list();
            } else if (hasPort) {
                calendars = HolidayCalendar.find("portCode", portCode).list();
            } else {
                calendars = HolidayCalendar.find("{}").list();
            }
            return Response.ok(calendars).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Get a single holiday calendar by ID.
     */
    @GET
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN", "ROLE_EDI", "ROLE_READONLY"})
    public Response getCalendar(@PathParam("id") String id) {
        HolidayCalendar cal = HolidayCalendar.findById(id);
        if (cal == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "HolidayCalendar not found: " + id, 404))
                    .build();
        }
        return Response.ok(cal).build();
    }

    /**
     * Create a new holiday calendar.
     */
    @POST
    @RolesAllowed({"ROLE_ADMIN"})
    public Response createCalendar(HolidayCalendar calendar) {
        if (calendar == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        try {
            calendar.persist();
            return Response.status(201).entity(calendar).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Update an existing holiday calendar.
     * Loads the existing document, overwrites all writable fields, and persists.
     */
    @PUT
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response updateCalendar(@PathParam("id") String id, HolidayCalendar incoming) {
        HolidayCalendar existing = HolidayCalendar.findById(id);
        if (existing == null) {
            return Response.status(404)
                    .entity(new ErrorResponse("NOT_FOUND", "HolidayCalendar not found: " + id, 404))
                    .build();
        }
        if (incoming == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("BAD_REQUEST", "Request body is required", 400))
                    .build();
        }
        try {
            existing.calendarName = incoming.calendarName;
            existing.countryCode = incoming.countryCode;
            existing.portCode = incoming.portCode;
            existing.year = incoming.year;
            existing.holidayDates = incoming.holidayDates;
            existing.notes = incoming.notes;
            existing.update();
            return Response.ok(existing).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    /**
     * Delete a holiday calendar by ID.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({"ROLE_ADMIN"})
    public Response deleteCalendar(@PathParam("id") String id) {
        try {
            boolean deleted = HolidayCalendar.delete("_id", id) > 0;
            if (!deleted) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "HolidayCalendar not found: " + id, 404))
                        .build();
            }
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }
}
