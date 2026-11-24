package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.service.DriverAffiliationStore;
import org.springframework.beans.factory.annotation.Autowired;
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
public class FleetBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, FleetLocationMessage> latest = new ConcurrentHashMap<>();
    /** Para no enviar a la torre las posiciones de los independientes (sin él, en pruebas, se envían todas). */
    private DriverAffiliationStore affiliations;

    public FleetBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Autowired(required = false)
    void setAffiliations(DriverAffiliationStore affiliations) {
        this.affiliations = affiliations;
    }

    /**
     * Reemplaza la posición pendiente del conductor: entre dos envíos solo cuenta la más reciente. Las de un conductor
     * independiente no van a la torre: no hace parte de la operación de cargas.
     */
    public void publish(FleetLocationMessage location) {
        if (affiliations != null && affiliations.isIndependent(location.getDriverId())) {
            return;
        }
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
