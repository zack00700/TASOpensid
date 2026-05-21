package fr.alb.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import fr.alb.billing.service.TemplateMigrationService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/migration")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Migration", description = "Data migration operations")
@RolesAllowed("ROLE_ADMIN")
public class MigrationResource {

    @Inject
    TemplateMigrationService migrationService;

    @POST
    @Path("/templates")
    @Operation(summary = "Migrate existing templates",
            description = "Migrate templates from Document structure to typed structure")
    @APIResponse(responseCode = "200", description = "Migration completed successfully")
    public Response migrateTemplates() {
        try {
            migrationService.migrateExistingTemplates();
            return Response.ok()
                    .entity(new MigrationResult("Templates migrated successfully", true))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MigrationResult("Migration failed: " + e.getMessage(), false))
                    .build();
        }
    }

    @GET
    @Path("/templates/validate")
    @Operation(summary = "Validate template structure",
            description = "Check if all templates have valid structure")
    @APIResponse(responseCode = "200", description = "Validation completed")
    public Response validateTemplates() {
        try {
            migrationService.validateTemplateStructure();
            return Response.ok()
                    .entity(new MigrationResult("Validation completed", true))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MigrationResult("Validation failed: " + e.getMessage(), false))
                    .build();
        }
    }

    @Inject
    fr.alb.sequence.service.SequenceMigrationService sequenceMigrationService;

    @POST
    @Path("/sequences")
    @Operation(summary = "Initialize invoice sequences and migrate existing invoices",
            description = "Creates default sequences, assigns draftNumbers to drafts without one, and reports legacy finals")
    @APIResponse(responseCode = "200", description = "Migration completed")
    public Response migrateSequences() {
        try {
            var result = sequenceMigrationService.run();
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new MigrationResult("Sequence migration failed: " + e.getMessage(), false))
                    .build();
        }
    }

    public static record MigrationResult(String message, boolean success) {}
}