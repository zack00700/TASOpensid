/**
 * CQRS read model owned by the {@code ai} context.
 *
 * <h2>Why this package exists</h2>
 * Write contexts ({@code billing}, {@code yard}, {@code berth}, {@code dd}, …)
 * shape their entities around business invariants — not around the shape of
 * reports, dashboards, or natural-language analytics. Forcing them to also
 * serve reporting queries creates accidental coupling: every new KPI becomes
 * a new index or denormalisation on a write-side collection.
 *
 * <p>Instead, the read side lives here. Projections observe domain events
 * published through {@link fr.alb.platform.event.DomainEventPublisher} and
 * maintain flat, query-optimised documents in their own Mongo collections
 * (prefixed {@code READMODEL_*}). Dashboards and Ask AI query those
 * collections directly; they never reach into write-context entities.
 *
 * <h2>How a projection is written</h2>
 * <ol>
 *     <li>Declare a {@link fr.alb.ai.readmodel.ReadModel} subclass with an
 *         {@code @MongoEntity(collection = "READMODEL_<NAME>")} annotation.
 *         Fields are denormalised — no references, no joins at query time.</li>
 *     <li>Declare a sibling {@code *Projection} CDI bean with a method like
 *         {@code void on(@Observes InvoiceFinalized e)} that upserts the
 *         read-model document. Exceptions are swallowed after logging: a
 *         failing projection must never block the write side.</li>
 *     <li>Expose the read model through
 *         {@link fr.alb.ai.readmodel.ReadModelResource} (or a context-specific
 *         dashboard resource that queries the read model, never the
 *         write-side entity).</li>
 * </ol>
 *
 * <h2>Durability and replay</h2>
 * Events are persisted in {@code DOMAIN_OUTBOX} before the CDI dispatch. When
 * a new projection lands, the outbox can be replayed to back-fill history
 * without touching the emitting contexts. Until a replay tool lands, new
 * projections start from "now" and only cover events emitted after deploy.
 *
 * <h2>Storage choice</h2>
 * MongoDB today (same cluster) because volume is modest and ops overhead
 * matters. The package stays free of joins and aggregations so a future move
 * to ClickHouse or DuckDB is a collection-by-collection lift, not a rewrite.
 */
package fr.alb.ai.readmodel;
