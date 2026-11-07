package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.service.AccountStatusStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Estado de la cuenta de los conductores a partir de los eventos de user-identity:
 *  - user.created de un CONDUCTOR: la cuenta nace PENDING_VERIFICATION.
 *  - user.status.changed: verificación (ACTIVE), suspensión o reactivación, y la fotografía que user-identity publica
 *    al arrancar. Como el grupo empieza en earliest, un fleets-drivers nuevo reconstruye el estado leyendo el tópico.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountEventListener {

    private final AccountStatusStore accountStatuses;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${truckcar.kafka.topic.user-events:truckcar.events.user-events}",
            groupId = "${truckcar.kafka.consumer.group-id:fleets-drivers-group}")
    public void handleUserEvent(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.warn("Evento de usuario descartado: no es JSON válido ({})", e.getOriginalMessage());
            return;
        }
        String type = event.path("eventType").asText("");
        JsonNode payload = event.path("payload");
        String userId = text(payload, "userId");
        if (userId == null) {
            return;
        }
        if ("user.status.changed".equals(type)) {
            String status = text(payload, "status");
            if (status != null) {
                accountStatuses.put(userId, status);
                log.info("Cuenta de {}: {}", userId, status);
            }
        } else if ("user.created".equals(type) && "CONDUCTOR".equals(text(payload, "role"))
                && accountStatuses.get(userId) == null) {
            // Si ya llegó un estado (p. ej. se verificó antes de procesar este evento), no se pisa.
            accountStatuses.put(userId, "PENDING_VERIFICATION");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
