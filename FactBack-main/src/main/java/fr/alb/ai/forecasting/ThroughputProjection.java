package fr.alb.ai.forecasting;

import fr.alb.berth.event.VesselBerthed;
import fr.alb.equipment.event.ShiftStarted;
import fr.alb.gate.event.GateIn;
import fr.alb.gate.event.GateOut;
import fr.alb.yard.event.ContainerMoved;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Increments {@link ThroughputSnapshot} counters in response to domain
 * events emitted by yard, gate, berth and equipment. Failures are logged
 * and swallowed — a broken forecast row must never block a business write.
 */
@ApplicationScoped
public class ThroughputProjection {

    private static final Logger LOG = Logger.getLogger(ThroughputProjection.class);
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    void onContainerMoved(@Observes ContainerMoved e)  { bump(ThroughputSnapshot.Metric.YARD_MOVES, e.occurredAt()); }
    void onGateIn(@Observes GateIn e)                  { bump(ThroughputSnapshot.Metric.GATE_IN, e.occurredAt()); }
    void onGateOut(@Observes GateOut e)                { bump(ThroughputSnapshot.Metric.GATE_OUT, e.occurredAt()); }
    void onVesselBerthed(@Observes VesselBerthed e)    { bump(ThroughputSnapshot.Metric.VESSEL_CALLS, e.occurredAt()); }
    void onShiftStarted(@Observes ShiftStarted e)      { bump(ThroughputSnapshot.Metric.SHIFTS_STARTED, e.occurredAt()); }

    private void bump(ThroughputSnapshot.Metric metric, Instant at) {
        try {
            String bucket = yearMonth(at);
            String key = metric.name();
            ThroughputSnapshot row = ThroughputSnapshot
                    .find("yearMonth = ?1 and metric = ?2", bucket, key)
                    .firstResult();
            if (row == null) {
                row = new ThroughputSnapshot();
                row.yearMonth = bucket;
                row.metric = key;
                row.value = 1;
                row.persist();
            } else {
                row.value += 1;
                row.update();
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "Failed to bump throughput snapshot for %s — forecast may drift", metric);
        }
    }

    private static String yearMonth(Instant at) {
        Instant effective = at != null ? at : Instant.now();
        return YEAR_MONTH.format(effective.atOffset(ZoneOffset.UTC));
    }
}
