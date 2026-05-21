package fr.alb.dao;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import fr.alb.dao.ItemDao.ItemFilterParams;
import fr.alb.dto.PagedResponse;
import fr.alb.dto.PaginationMetadata;
import fr.alb.dto.PaginationParams;
import fr.alb.yard.model.Item;
import fr.alb.yard.model.Lifecycle;
import io.quarkus.logging.Log;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ItemDaoImpl implements ItemDao {

    private static final Logger LOGGER = Logger.getLogger(ItemDaoImpl.class);

    @ConfigProperty(name = "app.timezone", defaultValue = "Europe/Paris")
    String timezone;

    private ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /**
     * Batch-loads all Lifecycle documents referenced by the last lifecycle ID of
     * each item and returns them as a map keyed by lifecycle id.
     *
     * This eliminates the N+1 query pattern: instead of one DB round-trip per item
     * inside computeStatus(), we issue a single "find by id set" query for the
     * whole list and pass the resulting map into computeStatus(Map).
     */
    private Map<String, Lifecycle> buildLifecycleCache(List<Item> items) {
        Set<String> lcIds = items.stream()
                .filter(i -> i.getLifeCycles() != null && !i.getLifeCycles().isEmpty())
                .map(i -> i.getLifeCycles().get(i.getLifeCycles().size() - 1))
                .collect(Collectors.toSet());

        if (lcIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Single batch query for all required lifecycle documents.
        List<Lifecycle> lifecycles = Lifecycle.find("_id in ?1", lcIds).list();
        return lifecycles.stream().collect(Collectors.toMap(lc -> lc.id, lc -> lc));
    }

    /** Applies computeStatus to every item using a shared batch-loaded cache. */
    private void applyStatusBatch(List<Item> items) {
        Map<String, Lifecycle> cache = buildLifecycleCache(items);
        items.forEach(i -> i.setStatus(i.computeStatus(cache)));
    }

    @Override
    @Transactional
    public boolean addItem(Item item) {
        try {
            item.persist();
            return true;
        } catch(Exception e) {
            Log.error("Failed to persist item", e);
            return false;
        }
    }

    /** @deprecated Use paginated ItemResource instead. Capped at 1000 records. */
    @Override
    @Deprecated
    public List<Item> getItems() {
        List<Item> items = Item.find("{}").page(io.quarkus.panache.common.Page.ofSize(1000)).list();
        if (items.size() == 1000) {
            LOGGER.warn("getItems() reached the 1000-record cap. Use paginated access.");
        }
        applyStatusBatch(items);
        return items;
    }

    @Override
    public Item getItem(String itemId) {
        Item item = Item.findById(itemId);
        if (item != null) {
            // Single-item path: computeStatus() issues at most one DB query, which is acceptable.
            item.setStatus(item.computeStatus());
        }
        return item;
    }

    @Override
    public List<Item> getItemsByBillOfLadingId(String billOfLadingId) {
        List<Item> items = Item.find("billOfLadingId", billOfLadingId).list();
        applyStatusBatch(items);
        return items;
    }

    @Override
    @Transactional
    public Item updateItem(Item item) {
        item.update();
        return item;
    }

    @Override
    public List<Item> findByIds(List<String> ids) {
        List<Item> items = Item.find("_id in ?1", ids).list();
        applyStatusBatch(items);
        return items;
    }

    @Override
    public long getContainerCount(String containerType, LocalDate startDate, LocalDate endDate) {
        ZoneId z = zone();
        Date start = Date.from(startDate.atStartOfDay(z).toInstant());
        Date end = Date.from(endDate.atStartOfDay(z).toInstant());
        return Item.find("type = ?1 and lastInspectionDate >= ?2 and lastInspectionDate <= ?3",
                containerType, start, end).count();
    }

    @Override
    @Transactional
    public boolean deleteItem(String itemId) {
        return Item.deleteById(itemId);
    }

    // NEW PAGINATION METHODS

    @Override
    public PagedResponse<Item> getItemsPaginated(PaginationParams paginationParams, ItemFilterParams filterParams) {
        try {
            // Build dynamic query based on filters
            String query = buildQuery(filterParams);
            Parameters parameters = buildParameters(filterParams);

            // Create the base query with filters
            PanacheQuery<Item> panacheQuery;
            if (query.isEmpty()) {
                // No filters - get all items
                panacheQuery = Item.findAll();
            } else {
                // Has filters - use query with parameters
                if (parameters != null) {
                    panacheQuery = Item.find(query, parameters);
                } else {
                    // This shouldn't happen, but fallback to no parameters
                    panacheQuery = Item.findAll();
                }
            }

            // Get total count efficiently (this uses MongoDB's countDocuments)
            long totalItems = panacheQuery.count();

            // Apply pagination (this uses MongoDB's skip/limit)
            List<Item> items = panacheQuery
                    .page(paginationParams.getPage() - 1, paginationParams.getSize()) // Panache uses 0-based pages
                    .list();

            // Batch-load all referenced Lifecycle documents before computing status
            // to avoid N+1 queries (one Lifecycle.findById per item).
            applyStatusBatch(items);

            // Create pagination metadata
            PaginationMetadata metadata = new PaginationMetadata(
                    paginationParams.getPage(),
                    paginationParams.getSize(),
                    totalItems
            );

            return new PagedResponse<>(items, metadata);

        } catch (Exception e) {
            Log.error("Failed to retrieve paginated items", e);
            throw new RuntimeException("Failed to retrieve paginated items", e);
        }
    }


    @Override
    public PagedResponse<Item> getItemsByBillOfLadingPaginated(String billOfLadingId, PaginationParams paginationParams) {
        try {
            PanacheQuery<Item> query = Item.find("billOfLadingId", billOfLadingId);

            long totalItems = query.count();
            List<Item> items = query
                    .page(paginationParams.getPage() - 1, paginationParams.getSize())
                    .list();

            // Batch-load all referenced Lifecycle documents before computing status
            // to avoid N+1 queries (one Lifecycle.findById per item).
            applyStatusBatch(items);

            PaginationMetadata metadata = new PaginationMetadata(
                    paginationParams.getPage(),
                    paginationParams.getSize(),
                    totalItems
            );

            return new PagedResponse<>(items, metadata);

        } catch (Exception e) {
            Log.error("Failed to retrieve items by bill of lading", e);
            throw new RuntimeException("Failed to retrieve items by bill of lading", e);
        }
    }

    @Override
    public long getItemCount() {
        return Item.count();
    }

    @Override
    public long getItemCountByStatus(String status) {
        return Item.count("status", status);
    }

    // HELPER METHODS FOR QUERY BUILDING

    private String buildQuery(ItemFilterParams filterParams) {
        List<String> conditions = new ArrayList<>();

        if (filterParams.getSearch() != null && !filterParams.getSearch().trim().isEmpty()) {
            // Search across multiple fields using regex for partial matches
            conditions.add("(itemNumber like :search or position like :search or notes like :search)");
        }

        if (filterParams.getItemType() != null && !filterParams.getItemType().trim().isEmpty()) {
            conditions.add("itemType = :itemType");
        }

        if (filterParams.getStatus() != null && !filterParams.getStatus().trim().isEmpty()) {
            conditions.add("status = :status");
        }

        if (filterParams.getOwnerId() != null && !filterParams.getOwnerId().trim().isEmpty()) {
            conditions.add("ownerId = :ownerId");
        }

        return String.join(" and ", conditions);
    }

    private Parameters buildParameters(ItemFilterParams filterParams) {
        // We need to build parameters only if we have filters
        Parameters parameters = null;

        if (filterParams.getSearch() != null && !filterParams.getSearch().trim().isEmpty()) {
            // Use regex pattern for case-insensitive partial matching
            String searchPattern = ".*" + filterParams.getSearch().trim() + ".*";
            parameters = (parameters == null)
                    ? Parameters.with("search", searchPattern)
                    : parameters.and("search", searchPattern);
        }

        if (filterParams.getItemType() != null && !filterParams.getItemType().trim().isEmpty()) {
            parameters = (parameters == null)
                    ? Parameters.with("itemType", filterParams.getItemType().trim())
                    : parameters.and("itemType", filterParams.getItemType().trim());
        }

        if (filterParams.getStatus() != null && !filterParams.getStatus().trim().isEmpty()) {
            parameters = (parameters == null)
                    ? Parameters.with("status", filterParams.getStatus().trim())
                    : parameters.and("status", filterParams.getStatus().trim());
        }

        if (filterParams.getOwnerId() != null && !filterParams.getOwnerId().trim().isEmpty()) {
            parameters = (parameters == null)
                    ? Parameters.with("ownerId", filterParams.getOwnerId().trim())
                    : parameters.and("ownerId", filterParams.getOwnerId().trim());
        }

        return parameters;
    }
}
