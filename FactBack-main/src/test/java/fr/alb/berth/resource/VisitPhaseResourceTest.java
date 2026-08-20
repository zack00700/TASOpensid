package fr.alb.berth.resource;

import fr.alb.berth.model.Hold;
import fr.alb.berth.model.Visit;
import fr.alb.type.HoldType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * TC-05.3a — PATCH /api/visit/{id}/phase. Validates the lifecycle transition
 * {@code Created → Active → Completed} plus Canceled as a non-terminal-only exit.
 */
@QuarkusTest
class VisitPhaseResourceTest {

    @BeforeEach
    void clean() {
        Hold.deleteAll();
        Visit.deleteAll();
    }

    private void openActiveHold(String visitId) {
        Hold h = new Hold();
        h.visitId = visitId;
        h.type = HoldType.CUSTOMS;
        h.reason = "Blocking forward advance for test";
        h.openedAt = Instant.now();
        h.openedBy = "test";
        h.persist();
    }

    private String persistVisit(String phase) {
        Visit v = new Visit();
        v.vesselName = "MV Phase";
        v.visitReference = "REF-PHASE-" + UUID.randomUUID().toString().substring(0, 6);
        v.phase = phase;
        v.persist();
        return v.getId();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void advance_createdToActive_returns200() {
        String id = persistVisit("Created");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Active"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void advance_activeToCompleted_returns200() {
        String id = persistVisit("Active");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Completed"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Completed"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void cancel_fromActive_returns200() {
        String id = persistVisit("Active");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Canceled"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Canceled"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void invalidTransition_completedToActive_returns409() {
        String id = persistVisit("Completed");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void invalidTransition_canceledToActive_returns409() {
        String id = persistVisit("Canceled");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void invalidTransition_createdToCompleted_returns409() {
        // Created → Completed is not allowed (must go through Active)
        String id = persistVisit("Created");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Completed"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void unknownVisit_returns404() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + UUID.randomUUID() + "/phase")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void malformedId_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/not-a-uuid/phase")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void missingPhase_returns400() {
        String id = persistVisit("Created");

        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void unknownPhaseValue_returns400() {
        String id = persistVisit("Created");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "TimeWarp"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void legacyNullPhase_treatedAsCreated_canAdvance() {
        // Persist a visit then null out the phase field at the document level.
        String id = persistVisit("Active");
        Visit.mongoDatabase().getCollection("VESSEL_VISIT")
            .updateOne(new org.bson.Document("_id", id),
                       new org.bson.Document("$unset", new org.bson.Document("phase", "")));

        // Visit with no phase: treated as Created, so → Active should be allowed.
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Active"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void corruptedDateField_doesNotBlockPhaseUpdate() {
        // Same pattern as TC-05.4 regression: a visit doc with an ISO-string
        // LocalDateTime field used to 500 anything that decoded the full POJO.
        String badId = UUID.randomUUID().toString();
        org.bson.Document doc = new org.bson.Document()
            .append("_id", badId)
            .append("vesselName", "MV Corrupt")
            .append("visitReference", "REF-CORRUPT")
            .append("phase", "Created")
            .append("eta", "2026-05-25T08:30:00.123Z"); // ISO string in a LocalDateTime field
        Visit.mongoDatabase().getCollection("VESSEL_VISIT").insertOne(doc);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + badId + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Active"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void advance_blockedByActiveHold_returns409HoldsActive() {
        String id = persistVisit("Active");
        openActiveHold(id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Completed"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(409)
            .body("error", equalTo("HOLDS_ACTIVE"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void advance_createdToActive_alsoBlockedByActiveHold() {
        String id = persistVisit("Created");
        openActiveHold(id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(409)
            .body("error", equalTo("HOLDS_ACTIVE"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void cancel_alwaysAllowed_evenWithActiveHolds() {
        String id = persistVisit("Active");
        openActiveHold(id);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Canceled"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Canceled"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void advance_succeedsAfterAllHoldsReleased() {
        String id = persistVisit("Active");
        Hold h = new Hold();
        h.visitId = id;
        h.type = HoldType.CUSTOMS;
        h.reason = "to be released";
        h.openedAt = Instant.now();
        h.openedBy = "test";
        h.releasedAt = Instant.now();
        h.releasedBy = "test";
        h.persist();

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Completed"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(200)
            .body("phase", equalTo("Completed"));
    }

    @Test
    @TestSecurity(user = "unauthed", roles = "ROLE_READONLY")
    void readonlyRole_isForbidden() {
        String id = persistVisit("Created");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("phase", "Active"))
        .when()
            .patch("/api/visit/" + id + "/phase")
        .then()
            .statusCode(403);
    }
}
