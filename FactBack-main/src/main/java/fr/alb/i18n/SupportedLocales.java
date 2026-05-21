package fr.alb.i18n;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Set;

public final class SupportedLocales {

    public static final Set<String> SUPPORTED = Set.of("en", "fr", "es");

    private SupportedLocales() {}

    public static boolean isSupported(String locale) {
        return locale != null && SUPPORTED.contains(locale);
    }

    public static void requireOrThrow(String locale) {
        if (!isSupported(locale)) {
            throw new WebApplicationException(
                    "Unsupported locale: " + locale + ". Supported: " + SUPPORTED,
                    Response.Status.BAD_REQUEST);
        }
    }
}
