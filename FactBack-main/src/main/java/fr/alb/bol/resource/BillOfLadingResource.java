package fr.alb.bol.resource;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import fr.alb.dao.ItemDao;
import fr.alb.dto.ErrorResponse;
import fr.alb.dto.PagedResponse;
import fr.alb.dto.PaginationParams;
import fr.alb.dto.PaginationMetadata;
import fr.alb.bol.model.BillOfLading;
import fr.alb.yard.model.Item;
import fr.alb.model.TransportInfo;
import fr.alb.model.TransportType;
import fr.alb.berth.model.Vessel;
import fr.alb.model.VesselSnapshot;
import fr.alb.berth.model.Visit;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.ZoneOffset;

@Path("billoflading")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BillOfLadingResource {

    private static final Logger LOG = Logger.getLogger(BillOfLadingResource.class);

    @Inject
    ItemDao itemDao;

    @POST
    public Response create(BillOfLading bill) {
        try {
            BillOfLading persisted = persistBillOfLading(bill);
            return Response.status(Response.Status.CREATED).entity(persisted).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @POST
    @Path("bulk")
    public Response createMany(List<BillOfLading> bills) {
        if (bills == null || bills.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", "At least one bill of lading is required", 400))
                    .build();
        }

        List<BillOfLading> persisted = new ArrayList<>();
        try {
            for (BillOfLading bill : bills) {
                if (bill == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "Bills cannot contain null entries", 400))
                            .build();
                }
                persisted.add(persistBillOfLading(bill));
            }
            return Response.status(Response.Status.CREATED).entity(persisted).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("BAD_REQUEST", e.getMessage(), 400))
                    .build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    private BillOfLading persistBillOfLading(BillOfLading bill) {
        if (bill == null) {
            throw new IllegalArgumentException("Bill of lading is required");
        }

        bill.persist();

        List<String> itemIds = new ArrayList<>();
        if (bill.getItems() != null) {
            for (Item item : bill.getItems()) {
                item.setBillOfLadingId(bill.getId());
                if (!itemDao.addItem(item)) {
                    throw new IllegalStateException("Failed to create item");
                }
                itemIds.add(item.getId());
            }
        }
        bill.setItemIds(itemIds);
        bill.update();
        return bill;
    }

    @GET
    public Response list(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("search") String search,
            @QueryParam("status") String status,
            @QueryParam("shipper") String shipper,
            @QueryParam("vessel") String vessel,
            @QueryParam("transportType") String transportType) {
        try {
            // Always use pagination by default
            PaginationParams paginationParams = PaginationParams.of(page, size);

            // Create filter parameters
            BillOfLadingFilterParams filterParams = new BillOfLadingFilterParams(
                    search, status, shipper, vessel, transportType);

            // Get paginated results
            PagedResponse<BillOfLading> pagedBills = getBillsPaginated(paginationParams, filterParams);

            return Response.ok(pagedBills).build();

        } catch(Exception e) {
            LOG.error("Failed to retrieve bills of lading", e);
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to retrieve bills of lading: " + e.getMessage(), 500))
                    .build();
        }
    }

    private PagedResponse<BillOfLading> getBillsPaginated(PaginationParams paginationParams, BillOfLadingFilterParams filterParams) {
        try {
            // Build dynamic query based on filters
            String query = buildQuery(filterParams);
            Parameters parameters = buildParameters(filterParams);

            // Create the base query with filters
            PanacheQuery<BillOfLading> panacheQuery;
            if (query.isEmpty()) {
                panacheQuery = BillOfLading.findAll();
            } else {
                if (parameters != null) {
                    panacheQuery = BillOfLading.find(query, parameters);
                } else {
                    panacheQuery = BillOfLading.findAll();
                }
            }

            // Get total count efficiently
            long totalItems = panacheQuery.count();

            // Apply pagination
            List<BillOfLading> bills = panacheQuery
                    .page(paginationParams.getPage() - 1, paginationParams.getSize())
                    .list();

            // Create pagination metadata
            PaginationMetadata metadata = new PaginationMetadata(
                    paginationParams.getPage(),
                    paginationParams.getSize(),
                    totalItems
            );

            return new PagedResponse<>(bills, metadata);

        } catch (Exception e) {
            LOG.error("Failed to retrieve paginated bills of lading", e);
            throw new RuntimeException("Failed to retrieve paginated bills of lading", e);
        }
    }

    private String buildQuery(BillOfLadingFilterParams filterParams) {
        List<String> conditions = new ArrayList<>();

        if (filterParams.getSearch() != null && !filterParams.getSearch().trim().isEmpty()) {
            // Search across multiple fields
            conditions.add("(blNumber like :search or shipper like :search or consignee like :search or vessel like :search or voyage like :search)");
        }

        if (filterParams.getStatus() != null && !filterParams.getStatus().trim().isEmpty()) {
            conditions.add("status = :status");
        }

        if (filterParams.getShipper() != null && !filterParams.getShipper().trim().isEmpty()) {
            conditions.add("shipper like :shipper");
        }

        if (filterParams.getVessel() != null && !filterParams.getVessel().trim().isEmpty()) {
            conditions.add("vessel like :vessel");
        }

        if (filterParams.getTransportType() != null && !filterParams.getTransportType().trim().isEmpty()) {
            conditions.add("transportType = :transportType");
        }

        return String.join(" and ", conditions);
    }

    private Parameters buildParameters(BillOfLadingFilterParams filterParams) {
        Parameters parameters = null;

        if (filterParams.getSearch() != null && !filterParams.getSearch().trim().isEmpty()) {
            String searchPattern = ".*" + filterParams.getSearch().trim() + ".*";
            parameters = (parameters == null)
                    ? Parameters.with("search", searchPattern)
                    : parameters.and("search", searchPattern);
        }

        if (filterParams.getStatus() != null && !filterParams.getStatus().trim().isEmpty()) {
            parameters = (parameters == null)
                    ? Parameters.with("status", filterParams.getStatus().trim())
                    : parameters.and("status", filterParams.getStatus().trim());
        }

        if (filterParams.getShipper() != null && !filterParams.getShipper().trim().isEmpty()) {
            String shipperPattern = ".*" + filterParams.getShipper().trim() + ".*";
            parameters = (parameters == null)
                    ? Parameters.with("shipper", shipperPattern)
                    : parameters.and("shipper", shipperPattern);
        }

        if (filterParams.getVessel() != null && !filterParams.getVessel().trim().isEmpty()) {
            String vesselPattern = ".*" + filterParams.getVessel().trim() + ".*";
            parameters = (parameters == null)
                    ? Parameters.with("vessel", vesselPattern)
                    : parameters.and("vessel", vesselPattern);
        }

        if (filterParams.getTransportType() != null && !filterParams.getTransportType().trim().isEmpty()) {
            parameters = (parameters == null)
                    ? Parameters.with("transportType", filterParams.getTransportType().trim())
                    : parameters.and("transportType", filterParams.getTransportType().trim());
        }

        return parameters;
    }

    // Filter parameters class
    public static class BillOfLadingFilterParams {
        private String search;
        private String status;
        private String shipper;
        private String vessel;
        private String transportType;

        public BillOfLadingFilterParams(String search, String status, String shipper, String vessel, String transportType) {
            this.search = search;
            this.status = status;
            this.shipper = shipper;
            this.vessel = vessel;
            this.transportType = transportType;
        }

        // Getters
        public String getSearch() { return search; }
        public String getStatus() { return status; }
        public String getShipper() { return shipper; }
        public String getVessel() { return vessel; }
        public String getTransportType() { return transportType; }
    }

    @GET
    @Path("{id}")
    public Response get(@PathParam("id") String id) {
        BillOfLading bl = BillOfLading.findById(id);
        if (bl == null) {
            return Response.status(404).build();
        }
        return Response.ok(bl).build();
    }

    @PUT
    @Path("{id}")
    public Response update(@PathParam("id") String id, BillOfLading incoming) {
        try {
            BillOfLading existing = BillOfLading.findById(id);
            if (existing == null) {
                return Response.status(404).build();
            }

            // Update BL scalar fields
            existing.setBlNumber(incoming.getBlNumber());
            existing.setStatus(incoming.getStatus());
            existing.setShipper(incoming.getShipper());
            existing.setConsignee(incoming.getConsignee());
            existing.setNotifyParty(incoming.getNotifyParty());
            existing.setTransportType(incoming.getTransportType());
            existing.setVessel(incoming.getVessel());
            existing.setVoyage(incoming.getVoyage());
            existing.setPortOfLoading(incoming.getPortOfLoading());
            existing.setPortOfDischarge(incoming.getPortOfDischarge());
            existing.setPlaceOfDelivery(incoming.getPlaceOfDelivery());
            existing.setDriver(incoming.getDriver());
            existing.setTrainNumber(incoming.getTrainNumber());
            existing.setTruckNumber(incoming.getTruckNumber());
            existing.setCommodity(incoming.getCommodity());
            // Commercial / documentation fields
            existing.setBookingNumber(incoming.getBookingNumber());
            existing.setShippingLine(incoming.getShippingLine());
            existing.setIncoterms(incoming.getIncoterms());
            existing.setFreightPayableAt(incoming.getFreightPayableAt());
            existing.setBolDate(incoming.getBolDate());
            existing.setHouseBolNumber(incoming.getHouseBolNumber());
            existing.setMasterBolNumber(incoming.getMasterBolNumber());

            // ✅ FIXED: Only process items if explicitly provided
            if (incoming.getItems() != null) {
                // Items were explicitly provided, treat as source of truth
                final boolean deleteMissingItems = true;

                java.util.List<String> previousIds = existing.getItemIds() != null
                        ? new java.util.ArrayList<>(existing.getItemIds())
                        : new java.util.ArrayList<>();

                java.util.List<Item> incomingItems = incoming.getItems();
                java.util.List<String> newIds = new java.util.ArrayList<>();

                // Process incoming items
                for (Item it : incomingItems) {
                    it.setBillOfLadingId(id);

                    if (it.getId() != null) {
                        Item db = itemDao.getItem(it.getId());
                        if (db != null) {
                            Item updated = itemDao.updateItem(it);
                            if (updated == null) {
                                return Response.status(500)
                                        .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to update item " + it.getId(), 500))
                                        .build();
                            }
                            newIds.add(updated.getId());
                        } else {
                            boolean created = itemDao.addItem(it);
                            if (!created) {
                                return Response.status(500)
                                        .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to create item (id missing in DB)", 500))
                                        .build();
                            }
                            newIds.add(it.getId());
                        }
                    } else {
                        boolean created = itemDao.addItem(it);
                        if (!created) {
                            return Response.status(500)
                                    .entity(new ErrorResponse("INTERNAL_ERROR", "Failed to create item", 500))
                                    .build();
                        }
                        newIds.add(it.getId());
                    }
                }

                // Delete items that were removed
                if (deleteMissingItems && !previousIds.isEmpty()) {
                    java.util.Set<String> keep = new java.util.HashSet<>(newIds);
                    for (String oldId : previousIds) {
                        if (!keep.contains(oldId)) {
                            itemDao.deleteItem(oldId);
                        }
                    }
                }

                // Update the item IDs
                existing.setItemIds(newIds);
            }
            // ✅ If incoming.getItems() is null, we preserve existing items completely

            existing.update();
            return Response.ok(existing).build();

        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        try {
            BillOfLading bl = BillOfLading.findById(id);
            if (bl == null) {
                return Response.status(404).build();
            }
            if (bl.getItemIds() != null) {
                for (String itemId : bl.getItemIds()) {
                    itemDao.deleteItem(itemId);
                }
            }
            BillOfLading.deleteById(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    // ... rest of your transport methods remain unchanged ...
    @PUT
    @Path("{id}/transport")
    public Response setTransport(@PathParam("id") String id, TransportInfo info) {
        try {
            BillOfLading bl = BillOfLading.findById(id);
            if (bl == null) {
                return Response.status(404).build();
            }
            if (info != null && info.type == TransportType.VESSEL) {
                if (info.vesselVisitId == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "vesselVisitId required", 400))
                            .build();
                }
                Visit visit = Visit.findById(info.vesselVisitId);
                if (visit == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(new ErrorResponse("BAD_REQUEST", "Vessel visit not found", 400))
                            .build();
                }
                Vessel vessel = visit.vesselId != null ? Vessel.findById(visit.vesselId) : null;

                VesselSnapshot snap = new VesselSnapshot();
                snap.vesselName = visit.vesselName;
                snap.imo = vessel != null ? vessel.imoNumber : null;
                snap.callSign = vessel != null ? vessel.callSign : null;
                snap.voyageIn = visit.inboundVoyage;
                snap.voyageOut = visit.outboundVoyage;
                snap.operator = visit.lineOperator != null ? visit.lineOperator : (vessel != null ? vessel.operator : null);
                snap.port = visit.pol;
                snap.terminal = visit.facility;
                snap.berth = null;
                snap.eta = visit.eta != null ? visit.eta.toInstant(ZoneOffset.UTC) : null;
                snap.etd = visit.etd != null ? visit.etd.toInstant(ZoneOffset.UTC) : null;
                snap.ata = visit.ata != null ? visit.ata.toInstant(ZoneOffset.UTC) : null;
                snap.atd = visit.atd != null ? visit.atd.toInstant(ZoneOffset.UTC) : null;

                TransportInfo persisted = new TransportInfo();
                persisted.type = TransportType.VESSEL;
                persisted.vesselVisitId = info.vesselVisitId;
                persisted.carrier = info.carrier;
                persisted.modeReference = info.modeReference;
                persisted.vessel = snap;
                bl.setTransport(persisted);
            } else {
                bl.setTransport(info);
            }
            bl.update();
            return Response.ok(bl.getTransport()).build();
        } catch (Exception e) {
            return Response.status(500)
                    .entity(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500))
                    .build();
        }
    }

    @POST
    @Path("{id}/transport/refresh")
    public Response refreshTransport(@PathParam("id") String id) {
        BillOfLading bl = BillOfLading.findById(id);
        if (bl == null || bl.getTransport() == null || bl.getTransport().vesselVisitId == null) {
            return Response.status(404).build();
        }
        Visit visit = Visit.findById(bl.getTransport().vesselVisitId);
        if (visit == null) {
            return Response.status(404).build();
        }
        Vessel vessel = visit.vesselId != null ? Vessel.findById(visit.vesselId) : null;
        VesselSnapshot snap = new VesselSnapshot();
        snap.vesselName = visit.vesselName;
        snap.imo = vessel != null ? vessel.imoNumber : null;
        snap.callSign = vessel != null ? vessel.callSign : null;
        snap.voyageIn = visit.inboundVoyage;
        snap.voyageOut = visit.outboundVoyage;
        snap.operator = visit.lineOperator != null ? visit.lineOperator : (vessel != null ? vessel.operator : null);
        snap.port = visit.pol;
        snap.terminal = visit.facility;
        snap.berth = null;
        snap.eta = visit.eta != null ? visit.eta.toInstant(ZoneOffset.UTC) : null;
        snap.etd = visit.etd != null ? visit.etd.toInstant(ZoneOffset.UTC) : null;
        snap.ata = visit.ata != null ? visit.ata.toInstant(ZoneOffset.UTC) : null;
        snap.atd = visit.atd != null ? visit.atd.toInstant(ZoneOffset.UTC) : null;
        bl.getTransport().vessel = snap;
        bl.update();
        return Response.ok(bl.getTransport()).build();
    }
}