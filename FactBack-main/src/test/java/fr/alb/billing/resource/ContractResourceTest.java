package fr.alb.billing.resource;

import fr.alb.billing.model.Contract;
import fr.alb.billing.testutil.ContractFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ContractResourceTest {

    @BeforeEach
    void clean() {
        Contract.deleteAll();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void postContract_201_whenAdminAndBodyValid() {
        Contract c = ContractFixtures.aContract();
        c.calculationMode.eventConfig.setId(null); // skip EventConfig existence validation

        given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("message", org.hamcrest.Matchers.equalTo("Contract created"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void postContract_400_whenNameIsMissing() {
        Contract c = ContractFixtures.aContract();
        c.name = null;

        given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void postContract_400_whenEventConfigIdDoesNotExist() {
        Contract c = ContractFixtures.aContract();
        c.calculationMode.eventConfig.setId("evt-does-not-exist-" + java.util.UUID.randomUUID());

        given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(400);
    }

    @Test
    void postContract_401_whenNoAuth() {
        Contract c = ContractFixtures.aContract();
        c.calculationMode.eventConfig.setId(null);

        given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.equalTo(401),
                org.hamcrest.Matchers.equalTo(403)));
    }

    @Test
    @TestSecurity(user = "viewer", roles = "ROLE_READONLY")
    void postContract_403_whenRoleIsReadonly() {
        Contract c = ContractFixtures.aContract();
        c.calculationMode.eventConfig.setId(null);

        given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getContractList_200_whenRoleIsUser() {
        given()
        .when()
            .get("/api/contract")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getContractById_404_whenIdDoesNotExist() {
        given()
        .when()
            .get("/api/contract/" + java.util.UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void putContract_200_whenAdminAndIdExists() {
        // Arrange: create a contract via the resource
        Contract c = ContractFixtures.aContract();
        c.calculationMode.eventConfig.setId(null);
        c.name = "Original-" + java.util.UUID.randomUUID();

        String id = given()
            .contentType(ContentType.JSON)
            .body(c)
        .when()
            .post("/api/contract")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Act: PUT with minimal body
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("description", "Updated description");

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .put("/api/contract/" + id)
        .then()
            .statusCode(200);
    }
}
