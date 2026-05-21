package fr.alb.yard.resource;

import fr.alb.type.EventScope;
import fr.alb.type.EventType;
import fr.alb.yard.model.EventConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class EventConfigResourceScopeFilterTest {

    @BeforeEach
    void clean() {
        EventConfig.deleteAll();
    }

    @Test
    @TestSecurity(user = "user", roles = "ROLE_USER")
    void getEvent_withScopeQuery_returnsOnlyMatchingAndBoth() {
        EventConfig item = new EventConfig("Gate-In", EventType.IN, true);
        item.setScope(EventScope.ITEM);
        item.persist();

        EventConfig vessel = new EventConfig("Pilot Boarded", EventType.INTERMEDIATE, false);
        vessel.setScope(EventScope.VESSEL);
        vessel.persist();

        EventConfig both = new EventConfig("Stevedoring Start", EventType.INTERMEDIATE, false);
        both.setScope(EventScope.BOTH);
        both.persist();

        given()
            .queryParam("scope", "VESSEL")
        .when()
            .get("/api/event")
        .then()
            .statusCode(200)
            .body("size()", org.hamcrest.Matchers.equalTo(2))
            .body("eventName", org.hamcrest.Matchers.hasItems("Pilot Boarded", "Stevedoring Start"));
    }
}
