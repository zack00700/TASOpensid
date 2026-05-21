package fr.alb.i18n;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import jakarta.ws.rs.WebApplicationException;

class SupportedLocalesTest {

    @Test
    void acceptsKnownLocales() {
        assertTrue(SupportedLocales.isSupported("en"));
        assertTrue(SupportedLocales.isSupported("fr"));
        assertTrue(SupportedLocales.isSupported("es"));
    }

    @Test
    void rejectsUnknownLocales() {
        assertFalse(SupportedLocales.isSupported("de"));
        assertFalse(SupportedLocales.isSupported("EN"));
        assertFalse(SupportedLocales.isSupported(""));
        assertFalse(SupportedLocales.isSupported(null));
    }

    @Test
    void requireOrThrowPasses() {
        SupportedLocales.requireOrThrow("fr");
    }

    @Test
    void requireOrThrowRaises400OnUnknown() {
        WebApplicationException ex = assertThrows(
                WebApplicationException.class,
                () -> SupportedLocales.requireOrThrow("zz")
        );
        assertTrue(ex.getResponse().getStatus() == 400);
    }
}
