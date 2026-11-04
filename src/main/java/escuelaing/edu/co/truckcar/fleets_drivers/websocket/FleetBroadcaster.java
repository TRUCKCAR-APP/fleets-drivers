package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agrega las posiciones de la torre de control (/topic/fleet-locations). En lugar de un mensaje STOMP por cada
 * posición recibida (con 1000 camiones, ~333 por segundo hacia cada coordinador), guarda la última de cada conductor y
 * cada pocos segundos envía un solo mensaje con un arreglo de las que cambiaron en esa ventana. La torre fusiona el
 * arreglo por driverId. El seguimiento de una carga (/topic/trip.{loadId}) sigue siendo inmediato: lo mira una sola
 * persona y la frecuencia es la de un camión.
 */
@Component
@RequiredArgsConstructor
public class FleetBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, FleetLocationMessage> latest = new ConcurrentHashMap<>();

    /** Reemplaza la posición pendiente del conductor: entre dos envíos solo cuenta la más reciente. */
    public void publish(FleetLocationMessage location) {
        latest.merge(location.getDriverId(), location, (pending, incoming) ->
                pending.getTimestamp() != null && incoming.getTimestamp() != null
                        && incoming.getTimestamp().isBefore(pending.getTimestamp()) ? pending : incoming);
    }

    /** Envía las posiciones de la ventana y las retira; una que llegue mientras tanto sale en el siguiente envío. */
    @Scheduled(fixedRateString = "${truckcar.telemetry.fleet-broadcast-interval-ms:3000}")
    public void flush() {
        List<FleetLocationMessage> snapshot = new ArrayList<>(latest.size());
        for (String driverId : latest.keySet()) {
            FleetLocationMessage location = latest.remove(driverId);
            if (location != null) {
                snapshot.add(location);
            }
        }
        if (!snapshot.isEmpty()) {
            messagingTemplate.convertAndSend(TelemetryAccessPolicy.FLEET_TOPIC, snapshot);
        }
    }
}
