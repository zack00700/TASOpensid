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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class VesselEventResourceTest {

    private String visitId;
    private String vesselEventConfigId;
    private String itemOnlyConfigId;

    @BeforeEach
    void seed() {
        VesselEvent.deleteAll();
        Visit.deleteAll();
        EventConfig.deleteAll();

        Visit v = new Visit();
        v.vesselName = "MV Alpha";
        v.visitReference = "REF-A";
        v.persist();
        visitId = v.getId();

        EventConfig vesselCfg = new EventConfig("Pilot Boarded", EventType.INTERMEDIATE, false);
        vesselCfg.setScope(EventScope.VESSEL);
        vesselCfg.persist();
        vesselEventConfigId = vesselCfg.getId();

        EventConfig itemCfg = new EventConfig("Gate-In", EventType.IN, true);
        itemCfg.setScope(EventScope.ITEM);
        itemCfg.persist();
        itemOnlyConfigId = itemCfg.getId();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_201_whenAllValid() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselEventConfigId,
                "eventDate", "2026-05-14T14:30:00Z",
                "notes", "Pilot embarked, fast tide"
            ))
        .when()
            .post("/api/visit/" + visitId + "/event")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_400_whenEventConfigIsItemOnly() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", itemOnlyConfigId,
                "eventDate", "2026-05-14T14:30:00Z",
                "notes", "Should be rejected"
            ))
        .when()
            .post("/api/visit/" + visitId + "/event")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_400_whenNotesEmpty() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselEventConfigId,
                "eventDate", "2026-05-14T14:30:00Z",
                "notes", ""
            ))
        .when()
            .post("/api/visit/" + visitId + "/event")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_404_whenVisitDoesNotExist() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselEventConfigId,
                "eventDate", "2026-05-14T14:30:00Z",
                "notes", "Should 404"
            ))
        .when()
            .post("/api/visit/" + java.util.UUID.randomUUID() + "/event")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void get_200_returnsEventsForVisit() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "eventId", vesselEventConfigId,
                "eventDate", "2026-05-14T14:30:00Z",
                "notes", "First event"
            ))
        .when().post("/api/visit/" + visitId + "/event")
        .then().statusCode(201);

        given()
        .when()
            .get("/api/visit/" + visitId + "/event")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].notes", equalTo("First event"));
    }
}
