package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Mantiene las cargas activas de cada conductor a partir de los eventos de Load-Allocation
 * (assignment.accepted, trip.started, trip.delivered, load.cancelled...). Solo usa el status de la carga
 * que trae el evento, así que cualquier evento con conductor asignado sirve para reconciliar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadLifecycleEventListener {

    private final IFleetDriverService fleetDriverService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${truckcar.kafka.topic.load-events:truckcar.events.load-allocation}",
            groupId = "${truckcar.kafka.consumer.group-id:fleets-drivers-group}")
    public void handleLoadEvent(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (JsonProcessingException e) {
            log.warn("Evento de carga descartado: no es JSON válido ({})", e.getOriginalMessage());
            return;
        }
        JsonNode payload = event.path("payload");
        String driverId = text(payload, "assignedDriverId");
        String loadStatus = text(payload, "loadStatus");
        Long loadId = payload.hasNonNull("loadId") ? payload.get("loadId").asLong() : null;
        if (driverId == null || loadId == null || loadStatus == null) {
            return;
        }
        fleetDriverService.applyLoadStatus(driverId, loadId, loadStatus);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
