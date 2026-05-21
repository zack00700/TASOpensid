package fr.alb.ais.service;

import fr.alb.ais.dto.AisMetadata;
import fr.alb.ais.dto.PositionReport;
import fr.alb.ais.dto.ShipStaticData;
import fr.alb.ais.model.VesselAisSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AisIngestionServiceTest {

    private static Clock fixed(String iso) {
        return Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
    }

    @Test
    void buildsSnapshotFromPositionReport() {
        AisMetadata meta = new AisMetadata();
        meta.mmsi = 211281000L;
        meta.shipName = "TEST";
        meta.timeUtc = "2026-05-08 07:00:00.000 +0000 UTC";

        PositionReport pr = new PositionReport();
        pr.userId = 211281000L;
        pr.latitude = 53.5;
        pr.longitude = 9.9;
        pr.sog = 8.4;
        pr.cog = 92.1;
        pr.trueHeading = 90.0;
        pr.navigationalStatus = 0;

        VesselAisSnapshot s = AisIngestionService.applyPositionReport(
            new VesselAisSnapshot(), meta, pr, fixed("2026-05-08T07:00:00Z"));

        assertEquals("211281000", s.mmsi);
        assertEquals(53.5, s.lat);
        assertEquals(9.9, s.lon);
        assertEquals(8.4, s.sog);
        assertEquals(0, s.navigationalStatus);
        assertEquals(Instant.parse("2026-05-08T07:00:00Z"), s.lastSeen);
        assertNotNull(s.positionTimestamp);
    }

    @Test
    void buildsSnapshotFromShipStaticDataResolvingEta() {
        AisMetadata meta = new AisMetadata();
        meta.mmsi = 211281000L;
        meta.timeUtc = "2026-05-08 07:00:00.000 +0000 UTC";

        ShipStaticData ssd = new ShipStaticData();
        ssd.userId = 211281000L;
        ssd.name = "TEST VESSEL";
        ssd.callSign = "ABC";
        ssd.imoNumber = 9876543L;
        ssd.destination = "FRLEH";
        ssd.eta = new ShipStaticData.Eta();
        ssd.eta.month = 6;
        ssd.eta.day = 15;
        ssd.eta.hour = 14;
        ssd.eta.minute = 30;

        VesselAisSnapshot s = AisIngestionService.applyShipStaticData(
            new VesselAisSnapshot(), meta, ssd, fixed("2026-05-08T07:00:00Z"));

        assertEquals("211281000", s.mmsi);
        assertEquals(9876543L, s.imoNumber);
        assertEquals("TEST VESSEL", s.name);
        assertEquals("ABC", s.callSign);
        assertEquals("FRLEH", s.destination);
        assertEquals(6, s.etaMonth);
        assertEquals(Instant.parse("2026-06-15T14:30:00Z"), s.resolvedEta);
        assertEquals(Instant.parse("2026-05-08T07:00:00Z"), s.lastSeen);
    }

    @Test
    void preservesExistingFieldsWhenPositionReportLacksThem() {
        VesselAisSnapshot existing = new VesselAisSnapshot();
        existing.mmsi = "211281000";
        existing.imoNumber = 9876543L;
        existing.destination = "FRLEH";
        existing.name = "OLD NAME";

        AisMetadata meta = new AisMetadata();
        meta.mmsi = 211281000L;
        meta.timeUtc = "2026-05-08 07:05:00.000 +0000 UTC";

        PositionReport pr = new PositionReport();
        pr.userId = 211281000L;
        pr.latitude = 53.6;
        pr.longitude = 10.0;
        pr.navigationalStatus = 5;

        VesselAisSnapshot s = AisIngestionService.applyPositionReport(
            existing, meta, pr, fixed("2026-05-08T07:05:00Z"));

        assertEquals(9876543L, s.imoNumber, "imoNumber should survive PositionReport update");
        assertEquals("FRLEH", s.destination, "destination should survive PositionReport update");
        assertEquals("OLD NAME", s.name, "name from prior static data should survive");
        assertEquals(53.6, s.lat);
        assertEquals(5, s.navigationalStatus);
    }
}
