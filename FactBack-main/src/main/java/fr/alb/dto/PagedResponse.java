package fr.alb.dto;

import java.util.List;

public class PagedResponse<T> {
    private List<T> items;
    private PaginationMetadata pagination;

    public PagedResponse() {}

    public PagedResponse(List<T> items, PaginationMetadata pagination) {
        this.items = items;
        this.pagination = pagination;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public PaginationMetadata getPagination() {
        return pagination;
    }

    public void setPagination(PaginationMetadata pagination) {
        this.pagination = pagination;
    }
}