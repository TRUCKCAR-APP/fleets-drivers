package escuelaing.edu.co.truckcar.fleets_drivers.controller;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetUnauthenticatedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.LoadOwnershipClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/fleets-drivers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FleetDriverController {

    private static final String AUTH_USER_HEADER = "X-Auth-User";
    private static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    private static final Set<String> FLEET_VIEWER_ROLES = Set.of("COORDINADOR", "ADMIN");

    private final IFleetDriverService service;
    private final LoadOwnershipClient loadOwnershipClient;

    /**
     * Estado y ubicación de un conductor: el propio CONDUCTOR (solo su driverId), la torre de control
     * (COORDINADOR o ADMIN) o un servicio interno (sin X-Auth-User).
     */
    @PostMapping("/state")
    public ResponseEntity<DriverVehicleStateResponse> updateState(
            @Valid @RequestBody UpdateDriverVehicleStateRequest request,
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireStateWriteAccess(request.getDriverId(), authenticatedUserId, authenticatedRole);
        return ResponseEntity.ok(service.saveOrUpdateState(request));
    }

    private static void requireStateWriteAccess(String driverId, String authenticatedUserId, String authenticatedRole) {
        boolean userRequest = authenticatedUserId != null && !authenticatedUserId.isBlank();
        if (!userRequest || (authenticatedRole != null && FLEET_VIEWER_ROLES.contains(authenticatedRole))) {
            return;
        }
        if (!"CONDUCTOR".equals(authenticatedRole)) {
            throw new FleetAccessDeniedException("El rol " + (authenticatedRole == null || authenticatedRole.isBlank()
                    ? "(sin rol)" : authenticatedRole) + " no puede actualizar el estado de la flota");
        }
        if (!authenticatedUserId.equals(driverId)) {
            throw new FleetAccessDeniedException("Un conductor solo puede actualizar su propio estado (driverId "
                    + driverId + " no corresponde al usuario autenticado)");
        }
    }

    /**
     * Estado y ubicación en vivo de un conductor. Lo ven la torre de control (COORDINADOR o ADMIN), el propio CONDUCTOR
     * y un GENERADOR solo si el conductor lleva una de sus cargas (la propiedad la confirma Load-Allocation).
     * Sin X-Auth-User es una llamada interna entre servicios (el seguimiento público de Load-Allocation): el api-gateway
     * responde 401 a toda petición sin JWT hacia estas rutas, así que desde fuera nunca llega una petición anónima.
     */
    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<DriverVehicleStateResponse> getByDriver(
            @PathVariable String driverId,
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            return ResponseEntity.ok(service.getStateByDriverId(driverId));
        }
        requireIdentity(authenticatedUserId, authenticatedRole);
        // El conductor se valida antes de buscar: así el 404 de "aún sin estado" solo lo recibe el dueño.
        if ("CONDUCTOR".equals(authenticatedRole) && !authenticatedUserId.equals(driverId)) {
            throw new FleetAccessDeniedException("Un conductor solo puede consultar su propio estado");
        }
        if ("GENERADOR".equals(authenticatedRole)) {
            requireGeneratorOwnsActiveLoad(driverId, authenticatedUserId);
        }
        requireKnownRole(authenticatedRole);
        return ResponseEntity.ok(service.getStateByDriverId(driverId));
    }

    @GetMapping("/vehicles/{plate}")
    public ResponseEntity<DriverVehicleStateResponse> getByPlate(
            @PathVariable String plate,
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireIdentity(authenticatedUserId, authenticatedRole);
        requireKnownRole(authenticatedRole);
        if (FLEET_VIEWER_ROLES.contains(authenticatedRole)) {
            return ResponseEntity.ok(service.getStateByPlate(plate));
        }
        DriverVehicleStateResponse state;
        try {
            state = service.getStateByPlate(plate);
        } catch (ResourceNotFoundException e) {
            // Sin distinguir "no existe" de "no es tuya": la placa no sirve para sondear la flota.
            throw new FleetAccessDeniedException("No tiene acceso al vehículo " + plate);
        }
        if ("CONDUCTOR".equals(authenticatedRole) && !authenticatedUserId.equals(state.getDriverId())) {
            throw new FleetAccessDeniedException("No tiene acceso al vehículo " + plate);
        }
        if ("GENERADOR".equals(authenticatedRole)) {
            requireGeneratorOwnsActiveLoad(state.getDriverId(), authenticatedUserId);
        }
        return ResponseEntity.ok(state);
    }

    /** Estas consultas exponen la ubicación: siempre son de un usuario que pasó por el gateway. */
    private static void requireIdentity(String authenticatedUserId, String authenticatedRole) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()
                || authenticatedRole == null || authenticatedRole.isBlank()) {
            throw new FleetUnauthenticatedException("Se requiere un usuario autenticado para consultar la ubicación de la flota");
        }
    }

    private static void requireKnownRole(String authenticatedRole) {
        if (!FLEET_VIEWER_ROLES.contains(authenticatedRole) && !"CONDUCTOR".equals(authenticatedRole)
                && !"GENERADOR".equals(authenticatedRole)) {
            throw new FleetAccessDeniedException("El rol " + authenticatedRole + " no puede consultar la flota");
        }
    }

    private void requireGeneratorOwnsActiveLoad(String driverId, String generatorUserId) {
        boolean ownsOne = service.getActiveLoadIds(driverId).stream()
                .anyMatch(loadId -> loadOwnershipClient.isOwnedByGenerator(loadId, generatorUserId));
        if (!ownsOne) {
            throw new FleetAccessDeniedException("Un generador solo puede ver a los conductores de sus cargas activas");
        }
    }

    /** Posición y estado de toda la flota: torre de control (COORDINADOR o ADMIN) y servicios internos. */
    @GetMapping("/active")
    public ResponseEntity<List<DriverVehicleStateResponse>> getAllActive(
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireFleetVisibility(authenticatedUserId, authenticatedRole);
        return ResponseEntity.ok(service.getAllActiveVehicles());
    }

    /** Conductores ACTIVO sin carga aceptada ni en ruta: candidatos para el motor de asignación. */
    @GetMapping("/available")
    public ResponseEntity<List<DriverVehicleStateResponse>> getAvailable(
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireFleetVisibility(authenticatedUserId, authenticatedRole);
        return ResponseEntity.ok(service.getAvailableForNewLoad());
    }

    /**
     * Una petición de usuario llega por el api-gateway con X-Auth-User y X-Auth-Role (el gateway descarta los valores
     * que envíe el cliente). Sin X-Auth-User es una llamada interna entre servicios (p. ej. el matching de AI-fleet).
     */
    private static void requireFleetVisibility(String authenticatedUserId, String authenticatedRole) {
        boolean userRequest = authenticatedUserId != null && !authenticatedUserId.isBlank();
        if (userRequest && (authenticatedRole == null || !FLEET_VIEWER_ROLES.contains(authenticatedRole))) {
            throw new FleetAccessDeniedException("Solo la torre de control (COORDINADOR o ADMIN) puede consultar la flota completa"
                    + " (rol actual: " + (authenticatedRole == null || authenticatedRole.isBlank() ? "sin rol" : authenticatedRole) + ")");
        }
    }
}