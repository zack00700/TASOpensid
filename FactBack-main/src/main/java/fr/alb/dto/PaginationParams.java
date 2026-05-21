package fr.alb.dto;

public class PaginationParams {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private int page;
    private int size;

    public PaginationParams() {
        this.page = DEFAULT_PAGE;
        this.size = DEFAULT_SIZE;
    }

    public PaginationParams(int page, int size) {
        this.page = Math.max(page, 1); // Ensure page is at least 1
        this.size = Math.min(Math.max(size, 1), MAX_SIZE); // Ensure size is between 1 and MAX_SIZE
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(page, 1); }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.min(Math.max(size, 1), MAX_SIZE); }

    public int getOffset() {
        return (page - 1) * size;
    }

    public static PaginationParams of(Integer page, Integer size) {
        return new PaginationParams(
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE
        );
    }
}