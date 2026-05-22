package fr.alb.yard.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import fr.alb.dao.ItemDao;
import fr.alb.dao.ItemDao.ItemFilterParams;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.ItemDto;
import fr.alb.dto.PagedResponse;
import fr.alb.dto.PaginationParams;
import fr.alb.dto.ItemResponseDTO;
import fr.alb.equipment.api.IsoContainerCodeRegistry;
import fr.alb.equipment.validation.ContainerTypeValidator;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.ItemEvent;
import fr.alb.yard.model.Lifecycle;
import fr.alb.type.LifeCycleStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;

@Path("items")
@RunOnVirtualThread
public class ItemResource {

    private static final Logger LOG = Logger.getLogger(ItemResource.class);

    @Inject
    ItemDao itemDao;

    @Inject
    ObjectMapper mapper;

    @Inject
    IsoContainerCodeRegistry isoContainerCodeRegistry;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("ROLE_ADMIN")
    public Response addItem(Item item) {
        Optional<Response> validation = ContainerTypeValidator.validate(
                item == null ? null : item.getContainerType(),
                isoContainerCodeRegistry::contains);
        if (validation.isPresent()) return validation.get();
        try {
            boolean created = itemDao.addItem(item);
            if (created) {
                return Response.status(201).entity("Item " + item.getId() + " created ").build();
            }
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to create item", 500))
                    .build();
        } catch(Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @PATCH
    @Path("/{id}")
    @Consumes("application/json-patch+json")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ROLE_ADMIN")
    public Response updateItem(@PathParam("id") String id, String patchJson) {
        try {
            Item current = itemDao.getItem(id);
            if (current == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Item not found with id: " + id, 404))
                        .build();
            }

            JsonPatch patch = JsonPatch.fromJson(mapper.readTree(patchJson));
            JsonNode patchedNode = patch.apply(mapper.valueToTree(current));
            Item patched = mapper.treeToValue(patchedNode, Item.class);

            // Preserve critical fields
            patched.setId(current.getId());
            if (patched.getItemType() == null && current.getItemType() != null) {
                patched.setItemType(current.getItemType());
            }
            if (patched.getType() == null && current.getType() != null) {
                patched.setType(current.getType());
            }

            Optional<Response> validation = ContainerTypeValidator.validate(
                    patched.getContainerType(),
                    isoContainerCodeRegistry::contains);
            if (validation.isPresent()) return validation.get();

            itemDao.updateItem(patched);
            return Response.ok(patched).build();

        } catch (JsonPatchException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "Invalid patch format: " + e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to update item: " + e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getItem(
            @PathParam("id") String id,
            @QueryParam("expandLifecycles") @DefaultValue("false") boolean expandLifecycles) {
        try {
            Item item = itemDao.getItem(id);
            if (item != null) {
                ItemResponseDTO response = ItemResponseDTO.fromItem(item, expandLifecycles);
                return Response.ok(response).build();
            } else {
                return Response.status(404).build();
            }
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getItems(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("search") String search,
            @QueryParam("itemType") String itemType,
            @QueryParam("status") String status,
            @QueryParam("ownerId") String ownerId,
            @QueryParam("expandLifecycles") @DefaultValue("false") boolean expandLifecycles) {
        try {
            PaginationParams paginationParams = PaginationParams.of(page, size);
            ItemFilterParams filterParams = new ItemFilterParams(search, itemType, status, ownerId);

            PagedResponse<Item> pagedItems = itemDao.getItemsPaginated(paginationParams, filterParams);

            if (expandLifecycles) {
                // Convert items to expanded responses
                List<ItemResponseDTO> expandedItems = pagedItems.getItems().stream()
                        .map(item -> ItemResponseDTO.fromItem(item, true))
                        .collect(Collectors.toList());

                // Create expanded response
                PagedResponse<ItemResponseDTO> expandedResponse = new PagedResponse<>(
                        expandedItems,
                        pagedItems.getPagination()
                );
                return Response.ok(expandedResponse).build();
            } else {
                return Response.ok(pagedItems).build();
            }

        } catch(Exception e) {
            LOG.error("Failed to retrieve items", e);
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to retrieve items: " + e.getMessage(), 500))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ROLE_ADMIN")
    public Response updateItemFull(@PathParam("id") String id, JsonNode incoming) {
        // Merge-update semantics: only keys present in the incoming JSON are written.
        // This is intentionally NOT a strict PUT (which would full-replace) because the
        // frontend currently sends a partial payload — full-replace would null out the
        // port/customs/commercial fields the form does not expose. Callers that need
        // to explicitly clear a field should use the PATCH endpoint above.
        try {
            if (incoming == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("BAD_REQUEST", "request body required", 400))
                        .build();
            }

            Item existing = itemDao.getItem(id);
            if (existing == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Item not found with id: " + id, 404))
                        .build();
            }

            // Reset the computed status before merging so the stored value (if any) wins
            // over the previously-derived display string when the payload omits it.
            existing.setStatus(null);
            mapper.readerForUpdating(existing).readValue(incoming);

            Optional<Response> validation = ContainerTypeValidator.validate(
                    existing.getContainerType(),
                    isoContainerCodeRegistry::contains);
            if (validation.isPresent()) return validation.get();

            existing.setId(id);
            itemDao.updateItem(existing);
            return Response.ok(existing).build();

        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to update item: " + e.getMessage(), 500))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ROLE_ADMIN")
    public Response deleteItem(@PathParam("id") String id) {
        try {
            boolean deleted = itemDao.deleteItem(id);
            if (deleted) {
                return Response.noContent().build();
            } else {
                return Response.status(404).build();
            }
        } catch(Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @GET
    @Path("/{id}/lifecycles")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getItemLifecycles(@PathParam("id") String id) {
        try {
            Item item = itemDao.getItem(id);
            if (item == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Item not found with id: " + id, 404))
                        .build();
            }
            List<String> lifecycleIds = item.getLifeCycles();
            if (lifecycleIds == null || lifecycleIds.isEmpty()) {
                return Response.ok(List.of()).build();
            }
            List<Lifecycle> lifecycles = lifecycleIds.stream()
                    .map(lcId -> (Lifecycle) Lifecycle.findById(lcId))
                    .filter(lc -> lc != null)
                    .collect(Collectors.toList());
            return Response.ok(lifecycles).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    public static class AddEventRequest {
        public String eventId;
        public String eventDate;
    }

    @POST
    @Path("/{id}/event")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER"})
    public Response addItemEvent(@PathParam("id") String id, AddEventRequest req) {
        try {
            Item item = itemDao.getItem(id);
            if (item == null) {
                return Response.status(404)
                        .entity(new ErrorResponse("NOT_FOUND", "Item not found with id: " + id, 404))
                        .build();
            }

            List<String> lifecycleIds = item.getLifeCycles();
            Lifecycle lifecycle = null;

            if (lifecycleIds != null && !lifecycleIds.isEmpty()) {
                String lastLifecycleId = lifecycleIds.get(lifecycleIds.size() - 1);
                lifecycle = Lifecycle.findById(lastLifecycleId);
            }

            // Auto-create an IN_PROGRESS lifecycle if none exists
            if (lifecycle == null) {
                lifecycle = new Lifecycle();
                lifecycle.setItemId(id);
                lifecycle.setStartTime(Instant.now());
                lifecycle.setStatus(LifeCycleStatus.IN_PROGRESS);
                lifecycle.persist();

                if (item.getLifeCycles() == null) {
                    item.setLifeCycles(new ArrayList<>());
                }
                item.getLifeCycles().add(lifecycle.getId());
                itemDao.updateItem(item);
            }

            ItemEvent event = new ItemEvent();
            event.setItemId(id);
            event.setEventId(req.eventId);
            event.setEventDate(Instant.parse(req.eventDate));
            event.persist();

            lifecycle.getEventIds().add(event.getId());
            lifecycle.update();

            return Response.status(201).entity(event).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    private static final int MAX_IDS = 200;

    @GET
    @Path("/by-ids")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_USER", "ROLE_READONLY"})
    public Response getByIds(@QueryParam("ids") List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "Query param 'ids' is required", 400))
                    .build();
        }

        List<String> filtered = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "Query param 'ids' is required", 400))
                    .build();
        }

        if (filtered.size() > MAX_IDS) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
        }

        List<ItemDto> dtos = itemDao.findByIds(filtered).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return Response.ok(dtos).build();
    }

    private ItemDto toDto(Item item) {
        ItemDto dto = new ItemDto();
        dto.id = item.getId();
        dto.itemNumber = item.getItemNumber();
        dto.type = item.getType();
        dto.ownerId = item.getOwnerId();
        dto.position = item.getPosition();
        dto.status = item.getStatus();
        if (dto.status == null && item.getItemStatus() != null) {
            dto.status = item.getItemStatus().getValue();
        }
        dto.lastInspectionDate = item.getLastInspectionDate();
        dto.nextInspectionDate = item.getNextInspectionDate();
        dto.notes = item.getNotes();
        Double weight = item.getWeight();
        if (weight == null && item.getAdditionalProperties() != null) {
            Object w = item.getAdditionalProperties().get("weightKg");
            if (w instanceof Number) {
                weight = ((Number) w).doubleValue();
            }
        }
        dto.weightKg = weight;
        Double volume = item.getVolume();
        if (volume == null && item.getAdditionalProperties() != null) {
            Object v = item.getAdditionalProperties().get("volumeM3");
            if (v instanceof Number) {
                volume = ((Number) v).doubleValue();
            }
        }
        dto.volumeM3 = volume;
        dto.billOfLadingId = item.getBillOfLadingId();
        return dto;
    }
}
