package fr.alb.equipment.validation;

import fr.alb.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ContainerTypeValidatorTest {

    private static final Predicate<String> KNOWN = code -> Set.of("22G1", "45G1", "22R1").contains(code);

    @Test
    void nullValueIsAllowed() {
        Optional<Response> result = ContainerTypeValidator.validate(null, KNOWN);
        assertTrue(result.isEmpty(), "null containerType should pass validation");
    }

    @Test
    void blankValueIsAllowed() {
        Optional<Response> result = ContainerTypeValidator.validate("   ", KNOWN);
        assertTrue(result.isEmpty(), "blank containerType should pass validation");
    }

    @Test
    void valueInRegistryPasses() {
        Optional<Response> result = ContainerTypeValidator.validate("22G1", KNOWN);
        assertTrue(result.isEmpty(), "registry value should pass");
    }

    @Test
    void valueNotInRegistryReturns400WithInvalidContainerType() {
        Optional<Response> result = ContainerTypeValidator.validate("GARBAGE", KNOWN);
        assertTrue(result.isPresent(), "unknown value should produce a 400 Response");
        Response r = result.get();
        assertEquals(400, r.getStatus());
        Object entity = r.getEntity();
        assertInstanceOf(ErrorResponse.class, entity);
        ErrorResponse err = (ErrorResponse) entity;
        // ErrorResponse stores the code in the "error" field (cf. fr.alb.dto.ErrorResponse).
        assertEquals("INVALID_CONTAINER_TYPE", err.getError());
        assertTrue(err.getMessage().contains("GARBAGE"), "error message should echo the offending value");
    }
}
