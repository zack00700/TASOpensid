package fr.alb.ais.service;

import fr.alb.ais.client.AisStreamClient;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AisStreamLifecycle {

    private static final Logger LOGGER = Logger.getLogger(AisStreamLifecycle.class);

    @Inject
    AisStreamClient client;

    @ConfigProperty(name = "app.ais.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "app.ais.aisstream.url")
    String url;

    // SmallRye Config in Quarkus 3.18 refuses to inject empty strings into String fields
    // (the property resolves to "" via ${VAR:} when env var is unset → conversion fails).
    // Wrapping in Optional sidesteps that — empty/unset → Optional.empty().
    @ConfigProperty(name = "app.ais.aisstream.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "app.ais.bbox")
    Optional<String> bboxJson;

    @ConfigProperty(name = "app.ais.mmsi-list")
    Optional<String> mmsiCsv;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            LOGGER.info("AisStreamLifecycle: AIS disabled (app.ais.enabled=false), skipping connect");
            return;
        }
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            LOGGER.warn("AisStreamLifecycle: AIS_ENABLED is true but AISSTREAM_API_KEY is empty — staying dormant");
            return;
        }
        connect();
    }

    @Scheduled(every = "${app.ais.heartbeat.every:off}")
    void heartbeat() {
        if (!enabled || apiKey.isEmpty() || apiKey.get().isBlank()) return;
        if (client.isConnected()) return;
        LOGGER.info("AisStreamLifecycle: WS not connected, attempting reconnect");
        connect();
    }

    private void connect() {
        List<String> mmsiList = AisStreamClient.parseMmsiList(mmsiCsv.orElse(""));
        try {
            client.connect(url, apiKey.get(), bboxJson.orElse(""), mmsiList);
            LOGGER.infof("AisStreamLifecycle: connected to %s (mmsiFilter=%d)", url, mmsiList.size());
        } catch (Exception e) {
            LOGGER.errorf(e, "AisStreamLifecycle: connect failed — will retry at next heartbeat");
        }
    }
}
