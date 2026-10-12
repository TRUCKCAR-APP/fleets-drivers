package escuelaing.edu.co.truckdar.fleets_drivers.controller;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckdar.fleets_drivers.service.IFleetDriverService;
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

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<DriverVehicleStateResponse> getByDriver(@PathVariable String driverId) {
        return ResponseEntity.ok(service.getStateByDriverId(driverId));
    }

    @GetMapping("/vehicles/{plate}")
    public ResponseEntity<DriverVehicleStateResponse> getByPlate(@PathVariable String plate) {
        return ResponseEntity.ok(service.getStateByPlate(plate));
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