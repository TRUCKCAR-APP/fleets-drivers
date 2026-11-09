package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cierra el ciclo de la IA vial: cuando route-ai-agent detecta que una alerta bloquea la vía (route.replan.requested
 * con la posición de la alerta), se reenvía en vivo a /topic/road-blockages. Cada PWA de conductor decide si el punto
 * cae sobre lo que le falta de ruta y recalcula; la torre de control lo muestra.
 * <p>
 * Los eventos de más de {@code max-age} se descartan: el grupo empieza en earliest y, al suscribirse por primera vez,
 * no debe avisar de bloqueos viejos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoadBlockageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    @Value("${truckcar.road-blockages.max-age:PT30M}")
    private Duration maxAge = Duration.ofMinutes(30);

    @KafkaListener(topics = "${truckcar.kafka.topic.route-requests:truckcar.events.route-requests}",
            groupId = "${truckcar.kafka.consumer.group-id:fleets-drivers-group}")
    public void handleRouteEvent(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.warn("Evento de ruta descartado: no es JSON válido ({})", e.getOriginalMessage());
            return;
        }
        if (!"route.replan.requested".equals(event.path("eventType").asText())) {
            return;
        }
        JsonNode payload = event.path("payload");
        if (!payload.path("latitude").isNumber() || !payload.path("longitude").isNumber()) {
            return; // sin posición la PWA no puede saber si el bloqueo está en su ruta
        }
        Instant occurredAt = instant(event.path("occurredAt").asText(null));
        if (occurredAt != null && occurredAt.isBefore(clock.instant().minus(maxAge))) {
            log.debug("Bloqueo viejo ({}) no se reenvía: eventId='{}'", occurredAt, event.path("eventId").asText());
            return;
        }
        Map<String, Object> blockage = new LinkedHashMap<>();
        blockage.put("eventId", event.path("eventId").asText(null));
        blockage.put("alertId", payload.path("alertId").isNumber() ? payload.get("alertId").asLong() : null);
        blockage.put("latitude", payload.get("latitude").asDouble());
        blockage.put("longitude", payload.get("longitude").asDouble());
        blockage.put("alertType", text(payload, "alertType"));
        blockage.put("title", text(payload, "title"));
        blockage.put("description", text(payload, "description"));
        blockage.put("severity", text(payload, "severity"));
        blockage.put("confidence", payload.path("confidence").isNumber() ? payload.get("confidence").asDouble() : null);
        blockage.put("source", text(payload, "source"));
        blockage.put("reason", text(payload, "reason"));
        blockage.put("affectedCorridor", text(payload, "affectedCorridor"));
        blockage.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        messagingTemplate.convertAndSend(TelemetryAccessPolicy.ROAD_BLOCKAGES_TOPIC, blockage);
        log.info("Bloqueo vial enviado a los conductores: alerta {} en ({}, {}) por {}", blockage.get("alertId"),
                blockage.get("latitude"), blockage.get("longitude"), blockage.get("source"));
    }

    /** Para las pruebas: fija el instante de referencia de la antigüedad del evento. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
