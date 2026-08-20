package fr.alb.berth.resource;

import fr.alb.berth.model.Visit;
import fr.alb.berth.model.VesselEvent;
import fr.alb.type.EventScope;
import fr.alb.type.EventType;
import fr.alb.yard.model.EventConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Probe test for the TC-05.4 production 500 on POST /api/visit/{visitId}/event.
 * Hammers the endpoint with the variations the tester could plausibly hit.
 * Every status code is recorded, and we fail loudly on any 500 to surface the
 * unhandled exception path.
 */
@QuarkusTest
class VesselEventResourceProbeTest {

    private String visitId;
    private String vesselCfgId;
    private String legacyCfgId; // EventConfig persisted without scope field at all

    @BeforeEach
    void seed() {
        VesselEvent.deleteAll();
        Visit.deleteAll();
        EventConfig.deleteAll();

        Visit v = new Visit();
        v.vesselName = "MV Probe";
        v.visitReference = "REF-PROBE";
        v.persist();
        visitId = v.getId();

        EventConfig vesselCfg = new EventConfig("Pilot Boarded", EventType.INTERMEDIATE, false);
        vesselCfg.setScope(EventScope.VESSEL);
        vesselCfg.persist();
        vesselCfgId = vesselCfg.getId();

        // Simulate a legacy config persisted before the scope feature: no scope at all.
        EventConfig legacyCfg = new EventConfig("Legacy Event", EventType.IN, true);
        legacyCfg.setScope(null);
        legacyCfg.persist();
        legacyCfgId = legacyCfg.getId();
    }

    /** Helper: returns the HTTP status code for one POST. */
    private int post(String body, String pathVisitId) {
        return given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/visit/" + pathVisitId + "/event")
        .then()
            .extract().statusCode();
    }

    private int postJson(Map<String, Object> payload, String pathVisitId) {
        // Use a real json mapper through restassured to avoid manual escaping issues.
        return given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/visit/" + pathVisitId + "/event")
        .then()
            .extract().statusCode();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void probe_variousMalformedPayloads_neverReturn500() {
        // Track which payloads return what, so we can see the picture if anything fails.
        Map<String, Integer> outcomes = new LinkedHashMap<>();

        // Helper to record + assert no 500.
        java.util.function.BiConsumer<String, Integer> rec = (label, code) -> {
            outcomes.put(label, code);
            if (code >= 500) {
                throw new AssertionError("Probe '" + label + "' returned " + code
                    + " — unhandled exception path found! All outcomes so far: " + outcomes);
            }
        };

        // 1. eventDate without Z, without seconds (raw datetime-local format)
        rec.accept("eventDate=datetime-local-raw",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30",
                "notes", "raw datetime-local format"), visitId));

        // 2. eventDate without Z, with seconds
        rec.accept("eventDate=no-tz-with-seconds",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00",
                "notes", "no timezone with seconds"), visitId));

        // 3. eventDate as garbage string
        rec.accept("eventDate=garbage",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "not-a-date",
                "notes", "garbage date"), visitId));

        // 4. eventDate as empty string
        rec.accept("eventDate=empty-string",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "",
                "notes", "empty date string"), visitId));

        // 5. eventDate as a number (epoch millis-style)
        Map<String, Object> p5 = new HashMap<>();
        p5.put("eventId", vesselCfgId);
        p5.put("eventDate", 1716490200000L);
        p5.put("notes", "epoch ms");
        rec.accept("eventDate=epoch-ms", postJson(p5, visitId));

        // 6. eventDate is null (explicit) -> validation 400
        Map<String, Object> p6 = new HashMap<>();
        p6.put("eventId", vesselCfgId);
        p6.put("eventDate", null);
        p6.put("notes", "explicit null date");
        rec.accept("eventDate=explicit-null", postJson(p6, visitId));

        // 7. Unknown extra field in payload
        Map<String, Object> p7 = new LinkedHashMap<>();
        p7.put("eventId", vesselCfgId);
        p7.put("eventDate", "2026-05-23T22:30:00Z");
        p7.put("notes", "with extra field");
        p7.put("someOtherField", "stray");
        rec.accept("payload=extra-field", postJson(p7, visitId));

        // 8. Legacy EventConfig (scope null in mongo) selected as eventId
        rec.accept("eventConfig=legacy-no-scope",
            postJson(Map.of(
                "eventId", legacyCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "legacy event config"), visitId));

        // 9. visitId not a UUID at all
        rec.accept("visitId=not-uuid",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "not-uuid path"), "this-is-not-a-uuid"));

        // 10. visitId with special chars
        rec.accept("visitId=special-chars",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "special chars path"), "some$weird/id"));

        // 11. eventId not a UUID
        rec.accept("eventId=not-uuid",
            postJson(Map.of(
                "eventId", "not-a-uuid",
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "eventId not uuid"), visitId));

        // 12. Notes with multibyte unicode
        rec.accept("notes=multibyte",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "Arrivée du pilote — 22h30 ✓ 港"), visitId));

        // 13. Notes exactly 500 chars (boundary)
        rec.accept("notes=500-chars-exact",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "a".repeat(500)), visitId));

        // 14. Notes 501 chars (just over)
        rec.accept("notes=501-chars",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "a".repeat(501)), visitId));

        // 15. Empty JSON body
        rec.accept("body=empty-json", post("{}", visitId));

        // 16. Body as JSON array (totally wrong shape)
        rec.accept("body=json-array", post("[1,2,3]", visitId));

        // 17. eventDate as ISO with milliseconds
        rec.accept("eventDate=iso-with-ms",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00.000Z",
                "notes", "iso with ms"), visitId));

        // 18. Notes only whitespace -> validation 400
        rec.accept("notes=whitespace-only",
            postJson(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-23T22:30:00Z",
                "notes", "    "), visitId));

        // If we got here, no 500 found.
        System.out.println("=== TC-05.4 Probe outcomes (no 500 detected) ===");
        outcomes.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void probe_realisticVisitWithAllDatesFilled_neverReturn500() {
        Visit v = new Visit();
        v.vesselName = "MV Realistic";
        v.visitReference = "REF-REAL";
        v.vesselId = "VSL-001";
        v.phase = "Created";
        v.service = "EUROPE-ASIA";
        v.serviceName = "Europe-Asia Express";
        v.facility = "TERMINAL-A";
        v.eta = java.time.LocalDateTime.of(2026, 5, 25, 8, 30);
        v.etd = java.time.LocalDateTime.of(2026, 5, 25, 18, 0);
        v.ata = java.time.LocalDateTime.of(2026, 5, 25, 8, 45);
        v.atd = java.time.LocalDateTime.of(2026, 5, 25, 18, 30);
        v.beginReceive = java.time.LocalDateTime.of(2026, 5, 22, 6, 0);
        v.dryCutoff = java.time.LocalDateTime.of(2026, 5, 24, 18, 0);
        v.reeferCutoff = java.time.LocalDateTime.of(2026, 5, 24, 20, 0);
        v.hazCutoff = java.time.LocalDateTime.of(2026, 5, 24, 16, 0);
        v.emptyPickup = java.time.LocalDateTime.of(2026, 5, 26, 8, 0);
        v.pol = "FRFOS";
        v.pod = "CNSHA";
        v.finalDestination = "Shanghai Yangshan";
        v.inboundVoyage = "001E";
        v.outboundVoyage = "001W";
        v.inboundCaptain = "Captain X";
        v.outboundCaptain = "Captain Y";
        v.lineOperator = "CMA-CGM";
        v.notes = "Realistic visit for probe";
        v.persist();

        int code = given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-25T14:30:00Z",
                "notes", "Pilot embarked on realistic visit"
            ))
        .when()
            .post("/api/visit/" + v.getId() + "/event")
        .then()
            .extract().statusCode();

        if (code >= 500) {
            throw new AssertionError("Realistic-visit probe returned " + code);
        }
        System.out.println("realistic-visit -> " + code);
    }

    /**
     * Simulate a legacy/corrupted Visit document in Mongo: store the raw doc with
     * String-typed date fields in formats LocalDateTime cannot parse. This is the
     * most plausible Cosmos-prod cause: a visit ingested by some other code path
     * (e.g. AIS) wrote ISO-with-Z into eta, which then makes Visit.findById throw.
     */
    /**
     * Simulate a legacy/corrupted Visit document in Mongo: insert the raw doc with
     * a String-typed date field in a format LocalDateTime cannot parse. This is the
     * most plausible Cosmos-prod cause: a visit written by another path (AIS?) put
     * an ISO-with-Z timestamp into eta, then Visit.findById fails to deserialize.
     */
    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void probe_visitDocWithCorruptedDateField_revealsAnyDeserializationCrash() {
        String badVisitId = UUID.randomUUID().toString();
        org.bson.Document doc = new org.bson.Document()
            .append("_id", badVisitId)
            .append("vesselName", "MV Corrupt")
            .append("visitReference", "REF-CORRUPT")
            .append("eta", "2026-05-25T08:30:00.123Z"); // ISO Instant-shaped string
        Visit.mongoDatabase().getCollection("VESSEL_VISIT").insertOne(doc);

        int code = given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselCfgId,
                "eventDate", "2026-05-25T14:30:00Z",
                "notes", "Recording on corrupt visit"
            ))
        .when()
            .post("/api/visit/" + badVisitId + "/event")
        .then()
            .extract().statusCode();

        System.out.println("corrupt-visit-doc -> " + code);
        if (code >= 500) {
            throw new AssertionError("REPRO FOUND — Visit doc with ISO-Z eta caused " + code);
        }
    }
}
