package fr.alb.ai.readmodel;

import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;

/**
 * Pre-aggregated revenue bucket — one document per (yearMonth, currency).
 * Powers the "monthly trend" chart without re-running a {@code $group}
 * aggregation over the entire invoice collection on every request.
 */
@MongoEntity(collection = "READMODEL_MONTHLY_REVENUE")
public class MonthlyRevenueBucket extends ReadModel {

    private static final long serialVersionUID = 1L;

    /** Bucket key, e.g. {@code "2026-04"}. */
    public String yearMonth;

    public String currency;

    /** Sum of finalised invoice totals in this bucket. */
    public BigDecimal totalAmount;

    /** Number of invoices counted into {@link #totalAmount}. */
    public long invoiceCount;
}
