package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cambios de estado de una carga en tiempo real (KAN-47): cada evento del ciclo de vida que publica Load-Allocation se
 * reenvía a /topic/trip.{loadId}.status, así la línea de tiempo de "Mis envíos" y la torre de control se actualizan al
 * instante en lugar de esperar al polling de 30 s.
 * <p>
 * Mismo permiso que las posiciones de la carga (TelemetryAccessPolicy): torre de control, el conductor con la carga
 * activa o el GENERADOR dueño. El destino usa "." como separador, como el resto de /topic/ del relay de RabbitMQ: la
 * routing key trip.{id}.status no coincide con la suscripción a trip.{id}, así que las posiciones y los estados no se
 * mezclan. Con varias réplicas, el grupo de Kafka entrega cada evento a una sola y el relay lo reparte a todas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadStatusBroadcaster {

    public static final String STATUS_SUFFIX = ".status";

    /** Eventos que cambian lo que ve el generador en la línea de tiempo. load.published no: aún nadie la sigue. */
    static final Set<String> LIFECYCLE_EVENTS = Set.of(
            "assignment.proposed", "assignment.notified", "assignment.accepted", "assignment.rejected",
            "assignment.timeout", "assignment.unassigned", "trip.started", "trip.delivered", "load.cancelled");

    private final SimpMessagingTemplate messagingTemplate;

    public static String destinationFor(long loadId) {
        return TelemetryAccessPolicy.TRIP_TOPIC_PREFIX + loadId + STATUS_SUFFIX;
    }

    /**
     * Publica el cambio si el evento es del ciclo de vida. Un fallo del broker no debe hacer reintentar el evento de
     * Kafka (el estado de la flota ya se aplicó y el polling de respaldo lo recupera): se registra y se sigue.
     */
    public void broadcast(JsonNode event) {
        String eventType = text(event, "eventType");
        JsonNode payload = event.path("payload");
        if (eventType == null || !LIFECYCLE_EVENTS.contains(eventType) || !payload.path("loadId").canConvertToLong()) {
            return;
        }
        long loadId = payload.get("loadId").asLong();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("loadId", loadId);
        message.put("eventType", eventType);
        message.put("loadStatus", text(payload, "loadStatus"));
        message.put("assignmentStatus", text(payload, "assignmentStatus"));
        message.put("assignedDriverName", text(payload, "assignedDriverName"));
        message.put("assignedVehiclePlate", text(payload, "assignedVehiclePlate"));
        message.put("assignmentAttempts", payload.path("assignmentAttempts").canConvertToInt()
                ? payload.get("assignmentAttempts").asInt() : null);
        message.put("occurredAt", text(event, "occurredAt"));
        try {
            messagingTemplate.convertAndSend(destinationFor(loadId), message);
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar {} de la carga #{} por WebSocket: {}", eventType, loadId, e.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
