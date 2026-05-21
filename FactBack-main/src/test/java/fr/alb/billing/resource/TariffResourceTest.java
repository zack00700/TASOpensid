package fr.alb.billing.resource;

import fr.alb.billing.model.Tariff;
import fr.alb.billing.testutil.ContractFixtures;
import fr.alb.type.ServiceType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TariffResourceTest {

    @BeforeEach
    void clean() {
        Tariff.deleteAll();
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void postTariff_201_whenAdminAndBodyValid() {
        Tariff t = ContractFixtures.aTariff();

        given()
            .contentType(ContentType.JSON)
            .body(t)
        .when()
            .post("/api/tariffs")
        .then()
            .statusCode(201)
            .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getTariffs_filtersByServiceType() {
        Tariff storage = ContractFixtures.aTariff();
        storage.serviceType = ServiceType.STORAGE;
        Tariff handling = ContractFixtures.aTariff();
        handling.serviceType = ServiceType.HANDLING;
        storage.persist();
        handling.persist();

        given()
            .queryParam("serviceType", "STORAGE")
        .when()
            .get("/api/tariffs")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("findAll { it.serviceType == 'HANDLING' }.size()", org.hamcrest.Matchers.equalTo(0));
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void deleteTariff_204_whenExists() {
        Tariff t = ContractFixtures.aTariff();
        t.persist();

        given()
        .when()
            .delete("/api/tariffs/" + t.getId())
        .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ROLE_ADMIN")
    void deleteTariff_404_whenIdDoesNotExist() {
        given()
        .when()
            .delete("/api/tariffs/" + java.util.UUID.randomUUID())
        .then()
            .statusCode(404);
    }
}
