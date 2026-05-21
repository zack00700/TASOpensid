package fr.alb.ais.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.ais.dto.AisEnvelope;
import fr.alb.ais.dto.PositionReport;
import fr.alb.ais.dto.ShipStaticData;
import fr.alb.ais.service.AisIngestionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Jakarta WebSocket @ClientEndpoint that connects to wss://stream.aisstream.io.
 * Lifecycle (connect/reconnect) is owned by AisStreamLifecycle; this class is a thin
 * I/O wrapper.
 *
 * Subscription protocol (per https://aisstream.io/documentation):
 * - First message after open is JSON containing APIKey + BoundingBoxes (+ optional
 *   FiltersShipMMSI, FilterMessageTypes), within ~3s of connect.
 * - Server then streams envelopes like { MessageType, MetaData, Message }.
 */
@ClientEndpoint
@ApplicationScoped
public class AisStreamClient {

    private static final Logger LOGGER = Logger.getLogger(AisStreamClient.class);
    private static final ObjectMapper OM = new ObjectMapper();

    @Inject
    AisIngestionService ingestionService;

    private final AtomicReference<Session> sessionRef = new AtomicReference<>();
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>();

    private String pendingSubscriptionJson;

    public boolean isConnected() {
        Session s = sessionRef.get();
        return s != null && s.isOpen();
    }

    public Instant lastMessageAt() {
        return lastMessageAt.get();
    }

    /**
     * Connect (or reconnect) and send the subscription frame. Throws on connection failure
     * — caller (AisStreamLifecycle) decides what to do.
     */
    public synchronized void connect(String url, String apiKey, String bboxJson, List<String> mmsiList) throws Exception {
        if (isConnected()) {
            LOGGER.debug("AisStreamClient: already connected, skipping");
            return;
        }
        this.pendingSubscriptionJson = buildSubscription(apiKey, bboxJson, mmsiList);
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, URI.create(url));
    }

    public synchronized void disconnect() {
        Session s = sessionRef.getAndSet(null);
        if (s != null && s.isOpen()) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        sessionRef.set(session);
        LOGGER.info("AisStreamClient: WS open, sending subscription");
        try {
            session.getBasicRemote().sendText(pendingSubscriptionJson);
        } catch (Exception e) {
            LOGGER.errorf(e, "AisStreamClient: failed to send subscription frame");
        }
    }

    @OnMessage
    public void onMessage(String payload) {
        lastMessageAt.set(Instant.now());
        try {
            AisEnvelope env = OM.readValue(payload, AisEnvelope.class);
            if (env.messageType == null || env.message == null) return;
            switch (env.messageType) {
                case "PositionReport" -> {
                    PositionReport pr = OM.treeToValue(env.message.get("PositionReport"), PositionReport.class);
                    ingestionService.onPositionReport(env, pr);
                }
                case "ShipStaticData" -> {
                    ShipStaticData ssd = OM.treeToValue(env.message.get("ShipStaticData"), ShipStaticData.class);
                    ingestionService.onShipStaticData(env, ssd);
                }
                default -> {
                    // Ignore other AIS message types (Phase 1 scope).
                }
            }
        } catch (Exception e) {
            LOGGER.errorf(e, "AisStreamClient: failed to handle message: %s",
                payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessionRef.compareAndSet(session, null);
        int code = reason.getCloseCode().getCode();
        // Code 1000 = normal closure (server-side rotation, etc.) — INFO; everything else WARN.
        if (code == 1000) {
            LOGGER.infof("AisStreamClient: WS closed (code=%d, reason=%s)", code, reason.getReasonPhrase());
        } else {
            LOGGER.warnf("AisStreamClient: WS closed (code=%d, reason=%s)", code, reason.getReasonPhrase());
        }
    }

    @OnError
    public void onError(Session session, Throwable t) {
        LOGGER.errorf(t, "AisStreamClient: WS error");
    }

    /** Builds the JSON subscription frame. Visible for unit testing if needed later. */
    static String buildSubscription(String apiKey, String bboxJson, List<String> mmsiList) {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("APIKey", apiKey);
        try {
            Object bbox = (bboxJson == null || bboxJson.isBlank())
                ? OM.readValue("[[[-90,-180],[90,180]]]", Object.class)
                : OM.readValue(bboxJson, Object.class);
            sub.put("BoundingBoxes", bbox);
        } catch (Exception e) {
            throw new IllegalArgumentException("AIS_BBOX is not valid JSON: " + bboxJson, e);
        }
        if (mmsiList != null && !mmsiList.isEmpty()) {
            List<String> trimmed = mmsiList.size() > 50 ? mmsiList.subList(0, 50) : mmsiList;
            sub.put("FiltersShipMMSI", trimmed);
        }
        sub.put("FilterMessageTypes", List.of("PositionReport", "ShipStaticData"));
        try {
            return OM.writeValueAsString(sub);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AIS subscription", e);
        }
    }

    /** Helper to parse comma-separated MMSI list from config. */
    public static List<String> parseMmsiList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Stream.of(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
