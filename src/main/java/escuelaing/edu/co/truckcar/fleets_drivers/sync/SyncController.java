package escuelaing.edu.co.truckcar.fleets_drivers.sync;

import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetUnauthenticatedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingesta por lotes de las posiciones GPS registradas sin conexión por la PWA del conductor. */
@RestController
@RequestMapping("/api/v1/fleets-drivers/sync")
@RequiredArgsConstructor
public class SyncController {

    private final FleetSyncService syncService;

    @PostMapping("/batch")
    public ResponseEntity<SyncBatchResponse> ingest(
            @RequestHeader(value = "X-Auth-User", required = false) String authenticatedUserId,
            @RequestHeader(value = "X-Auth-Role", required = false) String authenticatedRole,
            @Valid @RequestBody SyncBatchRequest request) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new FleetUnauthenticatedException("Se requiere un usuario autenticado para sincronizar posiciones");
        }
        if (!"CONDUCTOR".equals(authenticatedRole)) {
            throw new FleetAccessDeniedException("Solo un conductor sincroniza sus propias posiciones");
        }
        return ResponseEntity.ok(syncService.process(authenticatedUserId, request));
    }
}
