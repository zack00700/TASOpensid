package fr.alb.yard.resource;

import fr.alb.type.EventScope;
import fr.alb.type.EventType;
import fr.alb.yard.model.EventConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class EventConfigResourceCrudTest {

    @BeforeEach
    void clean() {
        EventConfig.deleteAll();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void deleteExistingEvent_returns204_andRemoves() {
        EventConfig e = new EventConfig("Gate-In", EventType.IN, true);
        e.setScope(EventScope.ITEM);
        e.persist();
        String id = e.getId();

        given().when().delete("/api/event/" + id).then().statusCode(204);

        given().when().get("/api/event/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void deleteUnknownEvent_returns404() {
        given()
        .when()
            .delete("/api/event/" + UUID.randomUUID().toString())
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void deleteMalformedId_returns400() {
        given()
        .when()
            .delete("/api/event/not-a-uuid")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void deleteAsUser_isForbidden() {
        EventConfig e = new EventConfig("Gate-In", EventType.IN, true);
        e.persist();

        given()
        .when()
            .delete("/api/event/" + e.getId())
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void updateExistingEvent_returns200_andUpdatesFields() {
        EventConfig e = new EventConfig("Old Name", EventType.IN, false);
        e.setScope(EventScope.ITEM);
        e.persist();
        String id = e.getId();

        String body = "{"
            + "\"eventName\":\"New Name\","
            + "\"eventType\":\"OUT\","
            + "\"billedEvent\":true,"
            + "\"scope\":\"BOTH\""
            + "}";

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .put("/api/event/" + id)
        .then()
            .statusCode(200)
            .body("eventName", Matchers.equalTo("New Name"))
            .body("eventType", Matchers.equalTo("OUT"))
            .body("billedEvent", Matchers.equalTo(true))
            .body("scope", Matchers.equalTo("BOTH"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void updateUnknownEvent_returns404() {
        String body = "{\"eventName\":\"X\",\"eventType\":\"IN\",\"billedEvent\":false}";
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .put("/api/event/" + UUID.randomUUID().toString())
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void updateMalformedId_returns400() {
        String body = "{\"eventName\":\"X\",\"eventType\":\"IN\",\"billedEvent\":false}";
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .put("/api/event/not-a-uuid")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void updateAsUser_isForbidden() {
        EventConfig e = new EventConfig("Old", EventType.IN, false);
        e.persist();
        String body = "{\"eventName\":\"X\",\"eventType\":\"IN\",\"billedEvent\":false}";
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .put("/api/event/" + e.getId())
        .then()
            .statusCode(403);
    }
}
