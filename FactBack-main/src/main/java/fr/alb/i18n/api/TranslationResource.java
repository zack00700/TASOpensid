package fr.alb.i18n.api;

import fr.alb.config.Roles;
import fr.alb.i18n.SupportedLocales;
import fr.alb.i18n.model.Translation;
import fr.alb.i18n.service.TranslationService;

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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.util.Map;

@Path("/i18n")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TranslationResource {

    @Inject
    TranslationService service;

    // ── Reads ────────────────────────────────────────────────────────────────

    @GET
    @Path("{locale}")
    @RolesAllowed({Roles.USER, Roles.READONLY, Roles.ADMIN})
    public Map<String, String> getMessages(@PathParam("locale") String locale) {
        SupportedLocales.requireOrThrow(locale);
        return service.mapForLocale(locale);
    }

    @GET
    @Path("{locale}/version")
    @RolesAllowed({Roles.USER, Roles.READONLY, Roles.ADMIN})
    public Map<String, String> getVersion(@PathParam("locale") String locale) {
        SupportedLocales.requireOrThrow(locale);
        Instant v = service.versionFor(locale);
        return Map.of("version", v != null ? v.toString() : "");
    }

    @GET
    @Path("{locale}/export")
    @RolesAllowed({Roles.USER, Roles.READONLY, Roles.ADMIN})
    public Response export(@PathParam("locale") String locale) {
        SupportedLocales.requireOrThrow(locale);
        Map<String, String> map = service.mapForLocale(locale);
        return Response.ok(map)
                .header("Content-Disposition", "attachment; filename=\"" + locale + ".json\"")
                .build();
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    public static class ValueBody {
        public String value;
    }

    @PUT
    @Path("{locale}/{key}")
    @RolesAllowed(Roles.ADMIN)
    public Response upsert(
            @PathParam("locale") String locale,
            @PathParam("key") String key,
            ValueBody body,
            @Context SecurityContext sec) {
        SupportedLocales.requireOrThrow(locale);
        if (key == null || key.isBlank()) {
            return Response.status(400).entity(Map.of("error", "key is required")).build();
        }
        String user = sec.getUserPrincipal() != null ? sec.getUserPrincipal().getName() : null;
        Translation result = service.upsert(locale, key, body != null ? body.value : null, user);
        return result == null
                ? Response.noContent().build()
                : Response.ok(result).build();
    }

    @DELETE
    @Path("{locale}/{key}")
    @RolesAllowed(Roles.ADMIN)
    public Response delete(@PathParam("locale") String locale, @PathParam("key") String key) {
        SupportedLocales.requireOrThrow(locale);
        boolean removed = service.delete(locale, key);
        return removed ? Response.noContent().build() : Response.status(404).build();
    }

    @POST
    @Path("{locale}/import")
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> bulkImport(
            @PathParam("locale") String locale,
            Map<String, String> body,
            @Context SecurityContext sec) {
        SupportedLocales.requireOrThrow(locale);
        String user = sec.getUserPrincipal() != null ? sec.getUserPrincipal().getName() : null;
        int updated = service.bulkImport(locale, body != null ? body : Map.of(), user);
        return Map.of("updated", updated);
    }
}
