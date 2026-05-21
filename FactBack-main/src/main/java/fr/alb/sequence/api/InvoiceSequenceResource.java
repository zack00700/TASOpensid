package fr.alb.sequence.api;

import fr.alb.sequence.dto.InvoiceSequenceCreateDTO;
import fr.alb.sequence.dto.InvoiceSequenceDTO;
import fr.alb.sequence.mapper.InvoiceSequenceMapper;
import fr.alb.sequence.model.InvoiceSequence;
import fr.alb.sequence.service.InvoiceSequenceService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/invoice-sequences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ROLE_ADMIN")
@Tag(name = "Invoice Sequences", description = "Manage invoice numbering sequences")
public class InvoiceSequenceResource {

    @Inject
    InvoiceSequenceService service;

    @Inject
    InvoiceSequenceMapper mapper;

    @GET
    @Operation(summary = "List all sequences")
    public List<InvoiceSequenceDTO> list() {
        return service.listAll().stream().map(mapper::toDTO).toList();
    }

    @GET
    @Path("/{sequenceId}")
    @Operation(summary = "Get sequence by ID")
    public InvoiceSequenceDTO get(@PathParam("sequenceId") String sequenceId) {
        return mapper.toDTO(service.findBySequenceId(sequenceId));
    }

    @GET
    @Path("/{sequenceId}/preview")
    @Operation(summary = "Preview next number without consuming it")
    public Response preview(@PathParam("sequenceId") String sequenceId) {
        return Response.ok(Map.of("preview", service.previewNextNumber(sequenceId))).build();
    }

    @POST
    @Operation(summary = "Create a new sequence")
    public Response create(@Valid InvoiceSequenceCreateDTO dto) {
        InvoiceSequence created = service.create(dto);
        return Response.status(Response.Status.CREATED)
            .entity(mapper.toDTO(created))
            .build();
    }

    @PUT
    @Path("/{sequenceId}")
    @Operation(summary = "Update an existing sequence")
    public InvoiceSequenceDTO update(
            @PathParam("sequenceId") String sequenceId,
            @Valid InvoiceSequenceCreateDTO dto) {
        return mapper.toDTO(service.update(sequenceId, dto));
    }
}
