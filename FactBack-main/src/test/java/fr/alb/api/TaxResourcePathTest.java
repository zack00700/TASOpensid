package fr.alb.api;

import fr.alb.billing.model.Tax;
import fr.alb.type.TaxType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the corrected path for the Tax CRUD endpoints. Both resources
 * previously declared {@code @Path("/api/…")} on top of
 * {@code @ApplicationPath("api")}, producing {@code /api/api/taxes} and a 404
 * when the frontend hit the documented {@code /api/taxes}.
 */
@QuarkusTest
class TaxResourcePathTest {

    @BeforeEach
    void clean() {
        Tax.deleteAll();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void post_api_taxes_isReachable_returns201() {
        Tax t = new Tax();
        t.setCode("TVA20");
        t.setName("TVA 20 %");
        t.setType(TaxType.PERCENTAGE);
        t.setRate(new BigDecimal("20"));
        t.setActive(true);

        given()
            .contentType(ContentType.JSON)
            .body(t)
        .when()
            .post("/api/taxes")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void get_api_taxes_isReachable_returns200() {
        given()
        .when()
            .get("/api/taxes")
        .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void doublePrefix_returns404() {
        // Pin the corrected behaviour: the broken doubled path must NOT
        // serve the resource.
        given()
        .when()
            .get("/api/api/taxes")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void get_api_taxCalculations_byContract_isReachable() {
        given()
        .when()
            .get("/api/tax-calculations/by-contract/" + java.util.UUID.randomUUID())
        .then()
            // Whatever the response code is on a missing contract, it must
            // NOT be 404 due to a routing-level miss. The current impl
            // returns 200 with an empty list when the contract has no
            // calculations — the assertion is just "the endpoint exists".
            .statusCode(org.hamcrest.Matchers.not(404));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void createdTax_persistsIsActiveFromJsonKey_andRoundTripsTheSameKey() {
        // Hotfix bug 2: Jackson was serialising the bean as `"active"`
        // (stripping the leading "is") while the frontend reads & sends
        // `"isActive"`. The @JsonProperty pin makes the JSON key match.
        String body = "{"
            + "\"code\":\"TVA20\","
            + "\"name\":\"TVA 20%\","
            + "\"type\":\"PERCENTAGE\","
            + "\"rate\":20,"
            + "\"isActive\":false"
            + "}";

        String id = given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/taxes")
        .then()
            .statusCode(201)
            .body("isActive", equalTo(false))
            .extract().path("id");

        given()
        .when()
            .get("/api/taxes/" + id)
        .then()
            .statusCode(200)
            .body("isActive", equalTo(false));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void updateTax_acceptsIsActiveToggle_andPersists() {
        // Create active by default
        String id = given()
            .contentType(ContentType.JSON)
            .body("{\"code\":\"TVA10\",\"name\":\"TVA 10%\",\"type\":\"PERCENTAGE\",\"rate\":10,\"isActive\":true}")
        .when()
            .post("/api/taxes")
        .then().statusCode(201).extract().path("id");

        // Toggle off
        given()
            .contentType(ContentType.JSON)
            .body("{\"isActive\":false}")
        .when()
            .put("/api/taxes/" + id)
        .then()
            .statusCode(200)
            .body("isActive", equalTo(false));

        // Reload to make sure the field stuck
        given().when().get("/api/taxes/" + id).then().body("isActive", equalTo(false));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void calculate_percentage_dividesRateBy100() {
        // Hotfix bug 1: 24000 × 15 % should be 3600, not 360000.
        String taxId = given()
            .contentType(ContentType.JSON)
            .body("{\"code\":\"HOTFIX15\",\"name\":\"15%\",\"type\":\"PERCENTAGE\",\"rate\":15,\"isActive\":true}")
        .when()
            .post("/api/taxes")
        .then().statusCode(201).extract().path("id");

        Float total = given()
            .contentType(ContentType.JSON)
            .body("{\"baseAmount\":24000,\"taxIds\":[\"" + taxId + "\"],\"inclusive\":false}")
        .when()
            .post("/api/taxes/calculate")
        .then()
            .statusCode(200)
            .extract().path("totalTaxAmount");

        assertEquals(3600.0f, total, 0.01f,
            "24000 × 15% must yield 3600 (was 360000 before /100 fix)");
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void calculate_fixedAmountIsUnchanged() {
        // Sanity check the FIXED_AMOUNT branch still bypasses the /100.
        String taxId = given()
            .contentType(ContentType.JSON)
            .body("{\"code\":\"FIXED5\",\"name\":\"Fixed 5\",\"type\":\"FIXED_AMOUNT\",\"rate\":5,\"isActive\":true}")
        .when()
            .post("/api/taxes")
        .then().statusCode(201).extract().path("id");

        Float total = given()
            .contentType(ContentType.JSON)
            .body("{\"baseAmount\":100,\"taxIds\":[\"" + taxId + "\"],\"inclusive\":false}")
        .when()
            .post("/api/taxes/calculate")
        .then()
            .statusCode(200)
            .extract().path("totalTaxAmount");

        assertEquals(5.0f, total, 0.01f, "FIXED_AMOUNT must stay flat, not divided by 100");
    }
}
