package fr.alb.ais.service;

import fr.alb.ais.dto.AisSuggestionDto;
import fr.alb.ais.model.VesselAisSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AisVisitMatcherTest {

    @Test
    void buildsFullDtoWhenSnapshotHasEverything() {
        VesselAisSnapshot snap = new VesselAisSnapshot();
        snap.mmsi = "211281000";
        snap.resolvedEta = Instant.parse("2026-05-12T14:30:00Z");
        snap.lastSeen = Instant.parse("2026-05-09T07:00:00Z");
        snap.navigationalStatus = 5; // Moored
        snap.lat = 14.6841;
        snap.lon = -17.4258;

        Optional<AisSuggestionDto> result = AisVisitMatcher.buildDto(snap);

        assertTrue(result.isPresent());
        AisSuggestionDto dto = result.get();
        assertEquals(Instant.parse("2026-05-12T14:30:00Z"), dto.suggestedEta);
        assertEquals(Instant.parse("2026-05-09T07:00:00Z"), dto.suggestedAta);
        assertEquals(Instant.parse("2026-05-09T07:00:00Z"), dto.sourceTimestamp);
        assertEquals(5, dto.navStatus);
        assertNotNull(dto.position);
        assertEquals(14.6841, dto.position.lat);
        assertEquals(-17.4258, dto.position.lon);
    }

    @Test
    void suggestedAtaIsNullWhenNavStatusIsNotMoored() {
        VesselAisSnapshot snap = new VesselAisSnapshot();
        snap.mmsi = "211281000";
        snap.resolvedEta = Instant.parse("2026-05-12T14:30:00Z");
        snap.lastSeen = Instant.parse("2026-05-09T07:00:00Z");
        snap.navigationalStatus = 0; // Under way using engine
        snap.lat = 14.6841;
        snap.lon = -17.4258;

        Optional<AisSuggestionDto> result = AisVisitMatcher.buildDto(snap);

        assertTrue(result.isPresent());
        AisSuggestionDto dto = result.get();
        assertEquals(Instant.parse("2026-05-12T14:30:00Z"), dto.suggestedEta);
        assertNull(dto.suggestedAta, "ATA should be null when navStatus != 5");
        assertEquals(0, dto.navStatus);
        assertNotNull(dto.position);
    }

    @Test
    void returnsEmptyWhenSnapshotHasNoUsefulFields() {
        VesselAisSnapshot snap = new VesselAisSnapshot();
        snap.mmsi = "211281000";
        snap.lastSeen = Instant.parse("2026-05-09T07:00:00Z");
        // resolvedEta = null, navigationalStatus = null, lat/lon = null

        Optional<AisSuggestionDto> result = AisVisitMatcher.buildDto(snap);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsDtoWithPositionOnlyWhenNoEtaAndNotMoored() {
        VesselAisSnapshot snap = new VesselAisSnapshot();
        snap.mmsi = "211281000";
        snap.lastSeen = Instant.parse("2026-05-09T07:00:00Z");
        // resolvedEta = null, navigationalStatus = null
        snap.lat = 14.6841;
        snap.lon = -17.4258;

        Optional<AisSuggestionDto> result = AisVisitMatcher.buildDto(snap);

        assertTrue(result.isPresent());
        AisSuggestionDto dto = result.get();
        assertNull(dto.suggestedEta);
        assertNull(dto.suggestedAta);
        assertEquals(Instant.parse("2026-05-09T07:00:00Z"), dto.sourceTimestamp);
        assertNull(dto.navStatus);
        assertNotNull(dto.position);
        assertEquals(14.6841, dto.position.lat);
    }
}
