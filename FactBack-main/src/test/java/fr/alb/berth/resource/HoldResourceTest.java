package fr.alb.berth.resource;

import fr.alb.berth.model.Hold;
import fr.alb.berth.model.Visit;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class HoldResourceTest {

    private String visitId;

    @BeforeEach
    void seed() {
        Hold.deleteAll();
        Visit.deleteAll();
        Visit v = new Visit();
        v.vesselName = "MV Hold";
        v.visitReference = "REF-HOLD";
        v.persist();
        visitId = v.getId();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void createHold_returns201_andPopulatesOpened() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "type", "Customs",
                "reason", "Awaiting customs clearance"
            ))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("visitId", equalTo(visitId))
            .body("type", equalTo("Customs"))
            .body("reason", equalTo("Awaiting customs clearance"))
            .body("openedBy", equalTo("user"))
            .body("openedAt", notNullValue())
            .body("releasedAt", Matchers.nullValue())
            .body("active", equalTo(true));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void create_400_whenTypeIsMissing() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("reason", "no type"))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void create_400_whenTypeIsUnknown() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "SuperWeirdType", "reason", "x"))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void create_400_whenReasonIsBlank() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Operational", "reason", "   "))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void create_400_whenVisitIdMalformed() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "x"))
        .when()
            .post("/api/visit/not-a-uuid/holds")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void create_404_whenVisitDoesNotExist() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "x"))
        .when()
            .post("/api/visit/" + UUID.randomUUID() + "/holds")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void list_returnsActiveAndReleasedSortedDescByOpenedAt() throws InterruptedException {
        // Create three holds back-to-back; openedAt must monotonically increase
        // because we use Instant.now() on the server side.
        given().contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "first"))
            .post("/api/visit/" + visitId + "/holds").then().statusCode(201);
        Thread.sleep(5);
        given().contentType(ContentType.JSON)
            .body(Map.of("type", "Financial", "reason", "second"))
            .post("/api/visit/" + visitId + "/holds").then().statusCode(201);
        Thread.sleep(5);
        given().contentType(ContentType.JSON)
            .body(Map.of("type", "Operational", "reason", "third"))
            .post("/api/visit/" + visitId + "/holds").then().statusCode(201);

        given()
        .when()
            .get("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(200)
            .body("size()", equalTo(3))
            .body("[0].reason", equalTo("third"))
            .body("[1].reason", equalTo("second"))
            .body("[2].reason", equalTo("first"));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void list_400_onMalformedVisitId() {
        given().when().get("/api/visit/not-a-uuid/holds").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void list_404_onUnknownVisit() {
        given().when().get("/api/visit/" + UUID.randomUUID() + "/holds").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void release_setsReleasedFields_andDeactivatesHold() {
        String holdId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "to be released"))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(201)
            .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("releaseNotes", "Clearance received"))
        .when()
            .patch("/api/holds/" + holdId + "/release")
        .then()
            .statusCode(200)
            .body("releasedAt", notNullValue())
            .body("releasedBy", equalTo("user"))
            .body("releaseNotes", equalTo("Clearance received"))
            .body("active", equalTo(false));
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void release_allowsEmptyBody() {
        String holdId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "no notes"))
            .post("/api/visit/" + visitId + "/holds")
            .then().statusCode(201).extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .patch("/api/holds/" + holdId + "/release")
        .then()
            .statusCode(200)
            .body("releasedAt", notNullValue())
            .body("releaseNotes", Matchers.nullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void release_409_whenAlreadyReleased() {
        String holdId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "x"))
            .post("/api/visit/" + visitId + "/holds")
            .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body("{}")
            .patch("/api/holds/" + holdId + "/release").then().statusCode(200);

        given().contentType(ContentType.JSON).body("{}")
        .when()
            .patch("/api/holds/" + holdId + "/release")
        .then()
            .statusCode(409);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void release_404_whenHoldUnknown() {
        given().contentType(ContentType.JSON).body("{}")
            .patch("/api/holds/" + UUID.randomUUID() + "/release")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void release_400_whenHoldIdMalformed() {
        given().contentType(ContentType.JSON).body("{}")
            .patch("/api/holds/not-a-uuid/release")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "readonly", roles = "ROLE_READONLY")
    void readonly_canList_butCannotCreate() {
        given().when().get("/api/visit/" + visitId + "/holds").then().statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("type", "Customs", "reason", "x"))
        .when()
            .post("/api/visit/" + visitId + "/holds")
        .then()
            .statusCode(403);
    }
}
