package fr.alb.berth.resource;

import fr.alb.berth.model.Visit;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * TC-05.2 — POST /api/visit must reject a payload missing visitReference.
 */
@QuarkusTest
class VisitResourceValidationTest {

    @BeforeEach
    void clean() {
        Visit.deleteAll();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_400_whenVisitReferenceIsMissing() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vesselName", "MV Test");
        payload.put("vesselId", "VSL-001");
        // visitReference deliberately omitted

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/visit")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_400_whenVisitReferenceIsBlank() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "vesselName", "MV Test",
                "vesselId", "VSL-002",
                "visitReference", "   "
            ))
        .when()
            .post("/api/visit")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void post_201_whenVisitReferenceIsPresent() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "vesselName", "MV Test",
                "vesselId", "VSL-003",
                "visitReference", "REF-001"
            ))
        .when()
            .post("/api/visit")
        .then()
            .statusCode(201);
    }
}
