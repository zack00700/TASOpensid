package fr.alb.dao;

import java.time.LocalDate;
import java.util.List;

import fr.alb.dto.PagedResponse;
import fr.alb.dto.PaginationParams;
import fr.alb.yard.model.Item;

public interface ItemDao {
        /**
         * Persist a new item.
         *
         * @param item the item to persist
         * @return {@code true} if the item was persisted, {@code false} otherwise
         */
        public boolean addItem(Item item);

        /**
         * Get all items (legacy method for backward compatibility)
         */
        public List<Item> getItems();

        /**
         * Get paginated items with optional filtering
         *
         * @param paginationParams pagination parameters (page, size)
         * @param filterParams filter parameters (search, itemType, status, ownerId)
         * @return paginated response with items and metadata
         */
        public PagedResponse<Item> getItemsPaginated(PaginationParams paginationParams, ItemFilterParams filterParams);

        public Item getItem(String itemId);

        public List<Item> getItemsByBillOfLadingId(String billOfLadingId);

        /**
         * Get paginated items by bill of lading ID
         */
        public PagedResponse<Item> getItemsByBillOfLadingPaginated(String billOfLadingId, PaginationParams paginationParams);

        public Item updateItem(Item item);

        public List<Item> findByIds(List<String> ids);

        public long getContainerCount(String containerType, LocalDate startDate, LocalDate endDate);

        public boolean deleteItem(String itemId);

        /**
         * Get total count of items for statistics
         */
        public long getItemCount();

        /**
         * Get count of items by status for dashboard
         */
        public long getItemCountByStatus(String status);

        // Inner class for filter parameters
        public static class ItemFilterParams {
                private String search;
                private String itemType;
                private String status;
                private String ownerId;

                public ItemFilterParams() {}

                public ItemFilterParams(String search, String itemType, String status, String ownerId) {
                        this.search = search;
                        this.itemType = itemType;
                        this.status = status;
                        this.ownerId = ownerId;
                }

                // Getters and setters
                public String getSearch() { return search; }
                public void setSearch(String search) { this.search = search; }

                public String getItemType() { return itemType; }
                public void setItemType(String itemType) { this.itemType = itemType; }

                public String getStatus() { return status; }
                public void setStatus(String status) { this.status = status; }

                public String getOwnerId() { return ownerId; }
                public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        }
}