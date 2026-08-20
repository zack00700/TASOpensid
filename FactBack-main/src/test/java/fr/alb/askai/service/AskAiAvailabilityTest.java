package fr.alb.askai.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the "is Ask AI usable?" decision. Plain unit test (no Quarkus
 * boot): the {@code @ConfigProperty} fields are package-private, so we set them
 * directly to exercise every branch of mode parsing and key/sentinel detection.
 */
class AskAiAvailabilityTest {

    private static AskAiAvailability with(String mode, String openAi, String anthropic) {
        AskAiAvailability a = new AskAiAvailability();
        a.enabledMode = mode;
        a.openAiKey = Optional.ofNullable(openAi);
        a.anthropicKey = Optional.ofNullable(anthropic);
        return a;
    }

    @Test
    void autoModeEnabledWhenAnyRealKeyPresent() {
        assertTrue(with("auto", "sk-real", null).isEnabled());
        assertTrue(with("auto", null, "anthropic-real").isEnabled());
    }

    @Test
    void autoModeDisabledWhenKeysMissingBlankOrSentinel() {
        assertFalse(with("auto", null, null).isEnabled());
        assertFalse(with("auto", "not-configured", "not-configured").isEnabled());
        assertFalse(with("auto", "   ", "").isEnabled());
        // sentinel match is case-insensitive
        assertFalse(with("auto", "NOT-CONFIGURED", null).isEnabled());
    }

    @Test
    void explicitTrueForcesEnabledEvenWithoutKeys() {
        assertTrue(with("true", null, null).isEnabled());
        assertTrue(with("on", "not-configured", null).isEnabled());
        assertTrue(with("YES", null, null).isEnabled());
    }

    @Test
    void explicitFalseForcesDisabledEvenWithKeys() {
        assertFalse(with("false", "sk-real", "anthropic-real").isEnabled());
        assertFalse(with("off", "sk-real", null).isEnabled());
        assertFalse(with("no", "sk-real", null).isEnabled());
    }

    @Test
    void nullOrUnknownModeFallsBackToKeyDetection() {
        assertTrue(with(null, "sk-real", null).isEnabled());
        assertFalse(with(null, null, null).isEnabled());
        assertTrue(with("bogus", "sk-real", null).isEnabled());
        assertFalse(with("bogus", null, null).isEnabled());
    }

    @Test
    void providerHelpersDistinguishOpenAiFromAnthropic() {
        AskAiAvailability a = with("auto", "sk-real", null);
        assertTrue(a.hasOpenAi());
        assertFalse(a.hasAnthropic());
        assertTrue(a.hasAnyKey());
    }
}
