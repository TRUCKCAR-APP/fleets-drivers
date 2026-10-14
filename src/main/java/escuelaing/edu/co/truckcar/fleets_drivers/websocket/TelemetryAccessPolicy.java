package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas de la telemetría en tiempo real:
 * <ul>
 *   <li>Publicar: un CONDUCTOR solo su propio driverId; COORDINADOR/ADMIN cualquiera (simulación); nadie más.</li>
 *   <li>/topic/fleet-locations: solo la torre de control (COORDINADOR/ADMIN), igual que GET /fleets-drivers/active.</li>
 *   <li>/topic/trip/{loadId}: torre de control, el conductor con esa carga activa o el GENERADOR dueño de la carga.</li>
 *   <li>/user/queue/errors: cualquier sesión (errores propios).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TelemetryAccessPolicy {

    public static final String FLEET_TOPIC = "/topic/fleet-locations";
    public static final String TRIP_TOPIC_PREFIX = "/topic/trip/";
    public static final String ERRORS_QUEUE = "/user/queue/errors";

    private static final Set<String> CONTROL_TOWER = Set.of("COORDINADOR", "ADMIN");
    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trip/(\\d{1,18})$");

    private final IFleetDriverService fleetDriverService;
    private final LoadOwnershipClient loadOwnershipClient;

    public boolean canPublish(TelemetryPrincipal principal, String driverId) {
        if (principal == null || driverId == null || driverId.isBlank()) {
            return false;
        }
        if (CONTROL_TOWER.contains(principal.role())) {
            return true;
        }
        return "CONDUCTOR".equals(principal.role()) && principal.userId().equals(driverId);
    }

    public boolean canSubscribe(TelemetryPrincipal principal, String destination) {
        if (principal == null || destination == null) {
            return false;
        }
        if (ERRORS_QUEUE.equals(destination)) {
            return true;
        }
        boolean controlTower = CONTROL_TOWER.contains(principal.role());
        if (FLEET_TOPIC.equals(destination)) {
            return controlTower;
        }
        Matcher trip = TRIP_TOPIC.matcher(destination);
        if (!trip.matches()) {
            return false;
        }
        if (controlTower) {
            return true;
        }
        Long loadId = Long.valueOf(trip.group(1));
        return switch (principal.role()) {
            case "CONDUCTOR" -> fleetDriverService.isActiveLoadOf(principal.userId(), loadId);
            case "GENERADOR" -> loadOwnershipClient.isOwnedByGenerator(loadId, principal.userId());
            default -> false;
        };
    }
}
