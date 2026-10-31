package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.PositionHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

/** Recibe la posición GPS de los conductores (/app/telemetry) y la retransmite a la torre y al seguimiento de la carga. */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TelemetryController {

    private final IFleetDriverService fleetDriverService;
    private final TelemetryAccessPolicy accessPolicy;
    private final SimpMessagingTemplate messagingTemplate;
    private final PositionHistory positionHistory;

    @MessageMapping("/telemetry")
    public void receiveLocation(@Payload LocationUpdateMessage update, Principal principal) {
        TelemetryPrincipal user = principal instanceof TelemetryPrincipal telemetryPrincipal ? telemetryPrincipal : null;
        if (!accessPolicy.canPublish(user, update.getDriverId())) {
            throw new TelemetryRejectedException("Un conductor solo puede enviar la ubicación de su propio driverId"
                    + " (driverId " + update.getDriverId() + ")");
        }
        validate(update);
        FleetLocationMessage location = fleetDriverService.recordLocation(update);
        positionHistory.recordLive(update, location.getTimestamp());
        messagingTemplate.convertAndSend(TelemetryAccessPolicy.FLEET_TOPIC, location);
        if (location.getLoadId() != null) {
            messagingTemplate.convertAndSend(TelemetryAccessPolicy.TRIP_TOPIC_PREFIX + location.getLoadId(), location);
        }
    }

    /** Los rechazos (permiso, datos inválidos, conductor sin vehículo) solo le llegan a quien envió la posición. */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, Object> handleError(Exception exception) {
        log.debug("Telemetría rechazada: {}", exception.getMessage());
        return Map.of(
                "error", exception instanceof TelemetryRejectedException ? "TELEMETRY_REJECTED" : "TELEMETRY_ERROR",
                "message", exception.getMessage() == null ? "Error procesando la ubicación" : exception.getMessage(),
                "timestamp", Instant.now().toString());
    }

    /** Lo usa también la ingesta offline (FleetSyncService): mismas reglas para posiciones en vivo y diferidas. */
    public static void validate(LocationUpdateMessage update) {
        Double latitude = update.getLatitude();
        Double longitude = update.getLongitude();
        if (latitude == null || longitude == null || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new TelemetryRejectedException("Coordenadas inválidas: latitude en [-90, 90] y longitude en [-180, 180]");
        }
        if (update.getSpeedKmH() != null && (update.getSpeedKmH() < 0 || update.getSpeedKmH() > 250)) {
            throw new TelemetryRejectedException("Velocidad inválida: debe estar entre 0 y 250 km/h");
        }
        if (update.getHeading() != null && (update.getHeading() < 0 || update.getHeading() > 360)) {
            throw new TelemetryRejectedException("Rumbo inválido: debe estar entre 0 y 360 grados");
        }
    }
}
