package fr.alb.ais.service;

import fr.alb.ais.dto.AisSuggestionDto;
import fr.alb.ais.model.VesselAisSnapshot;
import fr.alb.berth.model.Vessel;
import fr.alb.berth.model.Visit;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Resolves AIS suggestions for a vessel-visit by chaining
 * Visit → Vessel.mmsi → VesselAisSnapshot. The pure helper buildDto is
 * package-private and unit-tested; the Mongo-touching path is smoke-tested
 * manually via quarkus dev.
 */
@ApplicationScoped
public class AisVisitMatcher {

    /** AIS navigational status code 5 = "Moored". */
    static final int NAV_STATUS_MOORED = 5;

    public Optional<AisSuggestionDto> findSuggestionsFor(String visitId) {
        Visit visit = Visit.findById(visitId);
        if (visit == null || visit.vesselId == null) return Optional.empty();
        Vessel vessel = Vessel.findById(visit.vesselId);
        if (vessel == null || vessel.mmsi == null || vessel.mmsi.isBlank()) return Optional.empty();
        VesselAisSnapshot snap =
            VesselAisSnapshot.<VesselAisSnapshot>find("mmsi", vessel.mmsi).firstResult();
        if (snap == null) return Optional.empty();
        return buildDto(snap);
    }

    /** Pure converter — visible for unit tests. */
    static Optional<AisSuggestionDto> buildDto(VesselAisSnapshot snap) {
        var suggestedEta = snap.resolvedEta;
        var suggestedAta = (snap.navigationalStatus != null && snap.navigationalStatus == NAV_STATUS_MOORED)
            ? snap.lastSeen : null;
        boolean hasEta = suggestedEta != null;
        boolean hasAta = suggestedAta != null;
        boolean hasPos = snap.lat != null && snap.lon != null;
        if (!hasEta && !hasAta && !hasPos) return Optional.empty();

        AisSuggestionDto dto = new AisSuggestionDto();
        dto.suggestedEta = suggestedEta;
        dto.suggestedAta = suggestedAta;
        dto.sourceTimestamp = snap.lastSeen;
        dto.navStatus = snap.navigationalStatus;
        if (hasPos) {
            dto.position = new AisSuggestionDto.Position();
            dto.position.lat = snap.lat;
            dto.position.lon = snap.lon;
        }
        return Optional.of(dto);
    }
}
