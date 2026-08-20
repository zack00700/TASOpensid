package fr.alb.security;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Locks in the deny-by-default HTTP authorization policy declared in
 * {@code application.properties}. The baseline
 * {@code quarkus.http.auth.permission.authenticated.paths=/api/*} is the
 * fail-closed backstop for any resource that forgot its {@code @RolesAllowed}
 * annotation, so a missing annotation must never mean anonymous access.
 *
 * <p>Note that the {@code /api} prefix does not come from
 * {@code quarkus.http.root-path} (which is deliberately left commented out) but
 * from {@code @ApplicationPath("api")} on {@link fr.alb.TosBeApplication}. If
 * that annotation ever moves, the policy above stops matching and these tests
 * are what catches it.
 *
 * <p>The endpoints below are real, reachable routes on resources that carry no
 * security annotation at all — they are exactly what the backstop has to cover.
 */
@QuarkusTest
class HttpAuthorizationPolicyTest {

    /** {@code InvoicesResource} — no security annotation, real GET. */
    @Test
    void anonymousRead_onUnannotatedInvoicesResource_isRejected() {
        given()
        .when()
            .get("/api/invoices")
        .then()
            .statusCode(401);
    }

    /** {@code ItemEventResource} — no security annotation, real GET. */
    @Test
    void anonymousRead_onUnannotatedItemEventResource_isRejected() {
        given()
        .when()
            .get("/api/item/any-item-id/lifecycles")
        .then()
            .statusCode(401);
    }

    /** {@code LifecycleResource} — no security annotation, real state-changing POST. */
    @Test
    void anonymousWrite_onUnannotatedLifecycleResource_isRejected() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"anonymous attempt\"}")
        .when()
            .post("/api/lifecycle/any-lifecycle-id/cancel")
        .then()
            .statusCode(401);
    }

    /** Container/Azure liveness probes must stay reachable without a token. */
    @Test
    void healthProbe_staysPublic() {
        given()
        .when()
            .get("/q/health")
        .then()
            .statusCode(200);
    }
}
