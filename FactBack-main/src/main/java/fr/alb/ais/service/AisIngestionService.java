package fr.alb.ais.service;

import fr.alb.ais.dto.AisEnvelope;
import fr.alb.ais.dto.AisMetadata;
import fr.alb.ais.dto.PositionReport;
import fr.alb.ais.dto.ShipStaticData;
import fr.alb.ais.model.VesselAisSnapshot;
import fr.alb.ais.util.AisEtaResolver;
import fr.alb.berth.model.Vessel;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class AisIngestionService {

    private static final Logger LOGGER = Logger.getLogger(AisIngestionService.class);

    private final Clock clock;

    public AisIngestionService() {
        this.clock = Clock.systemUTC();
    }

    /** Test seam — never called in production. */
    AisIngestionService(Clock clock) {
        this.clock = clock;
    }

    public void onPositionReport(AisEnvelope env, PositionReport pr) {
        if (env == null || env.metadata == null || env.metadata.mmsi == null) return;
        String mmsi = String.valueOf(env.metadata.mmsi);
        VesselAisSnapshot existing = findSnapshot(mmsi).orElseGet(VesselAisSnapshot::new);
        VesselAisSnapshot updated = applyPositionReport(existing, env.metadata, pr, clock);
        upsert(updated);
    }

    public void onShipStaticData(AisEnvelope env, ShipStaticData ssd) {
        if (env == null || env.metadata == null || env.metadata.mmsi == null) return;
        String mmsi = String.valueOf(env.metadata.mmsi);
        VesselAisSnapshot existing = findSnapshot(mmsi).orElseGet(VesselAisSnapshot::new);
        VesselAisSnapshot updated = applyShipStaticData(existing, env.metadata, ssd, clock);
        upsert(updated);
        if (ssd != null && ssd.imoNumber != null) {
            autoDiscoverMmsiOnVessel(mmsi, ssd.imoNumber);
        }
    }

    /** Pure function — copies relevant fields from PositionReport into the snapshot. */
    static VesselAisSnapshot applyPositionReport(VesselAisSnapshot s, AisMetadata meta, PositionReport pr, Clock clock) {
        if (s.mmsi == null) s.mmsi = String.valueOf(meta.mmsi);
        if (pr != null) {
            s.lat = pr.latitude;
            s.lon = pr.longitude;
            s.sog = pr.sog;
            s.cog = pr.cog;
            s.trueHeading = pr.trueHeading;
            s.navigationalStatus = pr.navigationalStatus;
        }
        Instant ts = meta != null ? meta.timestamp() : null;
        s.positionTimestamp = ts != null ? ts : clock.instant();
        s.lastSeen = clock.instant();
        return s;
    }

    /** Pure function — copies relevant fields from ShipStaticData into the snapshot. */
    static VesselAisSnapshot applyShipStaticData(VesselAisSnapshot s, AisMetadata meta, ShipStaticData ssd, Clock clock) {
        if (s.mmsi == null) s.mmsi = String.valueOf(meta.mmsi);
        if (ssd != null) {
            if (ssd.imoNumber != null) s.imoNumber = ssd.imoNumber;
            if (ssd.name != null) s.name = ssd.name;
            if (ssd.callSign != null) s.callSign = ssd.callSign;
            if (ssd.destination != null) s.destination = ssd.destination;
            if (ssd.eta != null) {
                s.etaMonth = ssd.eta.month;
                s.etaDay = ssd.eta.day;
                s.etaHour = ssd.eta.hour;
                s.etaMinute = ssd.eta.minute;
                AisEtaResolver.resolve(ssd.eta.month, ssd.eta.day, ssd.eta.hour, ssd.eta.minute, clock)
                    .ifPresent(eta -> s.resolvedEta = eta);
            }
        }
        s.lastSeen = clock.instant();
        return s;
    }

    private Optional<VesselAisSnapshot> findSnapshot(String mmsi) {
        return Optional.ofNullable(VesselAisSnapshot.<VesselAisSnapshot>find("mmsi", mmsi).firstResult());
    }

    private void upsert(VesselAisSnapshot s) {
        // EntityBase mints a UUID in its constructor, so a freshly-created snapshot has a
        // non-null id that doesn't yet match any document. persistOrUpdate uses replaceOne
        // with upsert=true, so it inserts on first sight and replaces on subsequent reports.
        s.persistOrUpdate();
    }

    /**
     * If a Vessel exists with imoNumber matching the AIS-supplied IMO and has no MMSI yet,
     * fill it in. Idempotent: never overwrites a non-empty mmsi.
     * Tries both "IMO1234567" (frontend-validated format) and bare numeric for legacy data.
     */
    private void autoDiscoverMmsiOnVessel(String mmsi, Long aisImo) {
        String imoWithPrefix = "IMO" + aisImo;
        long updated = Vessel.update("mmsi = ?1", mmsi)
            .where("(imoNumber = ?1 or imoNumber = ?2) and (mmsi is null or mmsi = '')",
                imoWithPrefix, String.valueOf(aisImo));
        if (updated > 0) {
            LOGGER.infof("AIS auto-discovery: linked MMSI %s to %d Vessel(s) with IMO %d",
                mmsi, updated, aisImo);
        }
    }
}
