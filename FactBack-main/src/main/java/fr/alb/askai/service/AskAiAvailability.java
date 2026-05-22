package fr.alb.askai.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Centralizes the "is the Ask AI feature usable?" decision.
 *
 * <p>The feature requires at least one configured LLM key. Quarkus resolves missing
 * env vars to the literal sentinel {@code not-configured} (see application.properties),
 * which is treated as absent here.
 */
@ApplicationScoped
public class AskAiAvailability {

    private static final String SENTINEL_MISSING = "not-configured";

    @ConfigProperty(name = "app.askai.enabled", defaultValue = "auto")
    String enabledMode;

    @ConfigProperty(name = "openai.api.key", defaultValue = "")
    Optional<String> openAiKey;

    @ConfigProperty(name = "anthropic.api.key", defaultValue = "")
    Optional<String> anthropicKey;

    public boolean isEnabled() {
        return switch (enabledMode == null ? "auto" : enabledMode.trim().toLowerCase()) {
            case "true", "on", "yes"  -> true;
            case "false", "off", "no" -> false;
            default                   -> hasAnyKey();
        };
    }

    public boolean hasAnyKey() {
        return isConfigured(openAiKey) || isConfigured(anthropicKey);
    }

    public boolean hasOpenAi()    { return isConfigured(openAiKey); }
    public boolean hasAnthropic() { return isConfigured(anthropicKey); }

    private static boolean isConfigured(Optional<String> v) {
        if (v == null || v.isEmpty()) return false;
        String s = v.get().trim();
        return !s.isEmpty() && !SENTINEL_MISSING.equalsIgnoreCase(s);
    }
}
