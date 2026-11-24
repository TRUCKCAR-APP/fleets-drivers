package escuelaing.edu.co.truckcar.fleets_drivers.controller;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetUnauthenticatedException;
import escuelaing.edu.co.truckcar.fleets_drivers.service.DriverAffiliationStore;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.UserIdentityClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * «Mi flota» de una empresa transportadora: posición, estado, carga y jornada de SUS conductores, nunca los de otra
 * empresa. La empresa la confirma User-Identity con el token del usuario; los conductores se filtran por la afiliación
 * que User-Identity publica (user.affiliation.changed) y solo cuentan los que tienen la afiliación ACTIVA.
 * La flota completa sigue siendo solo de la torre de control (FleetDriverController /active).
 */
@RestController
@RequestMapping("/api/v1/fleets-drivers")
@RequiredArgsConstructor
public class CompanyFleetController {

    private static final String AUTH_USER_HEADER = "X-Auth-User";
    private static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    static final String COMPANY_ROLE = "TRANSPORTADORA";
    static final String ACTIVE_AFFILIATION = "ACTIVA";

    private final IFleetDriverService service;
    private final DriverAffiliationStore affiliations;
    private final UserIdentityClient userIdentity;

    @GetMapping("/company/fleet")
    public ResponseEntity<List<DriverVehicleStateResponse>> myFleet(
            @RequestHeader(value = AUTH_USER_HEADER, required = false) String authenticatedUserId,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            throw new FleetUnauthenticatedException("Se requiere un usuario autenticado para ver la flota de la empresa");
        }
        if (!COMPANY_ROLE.equals(authenticatedRole)) {
            throw new FleetAccessDeniedException("Solo una cuenta de transportadora puede ver su flota");
        }
        String companyId = userIdentity.companyIdOf(authorization);
        List<DriverVehicleStateResponse> own = service.getAllActiveVehicles().stream()
                .filter(driver -> belongsTo(driver.getDriverId(), companyId))
                .toList();
        return ResponseEntity.ok(own);
    }

    private boolean belongsTo(String driverId, String companyId) {
        DriverAffiliationStore.Affiliation affiliation = driverId == null ? null : affiliations.get(driverId);
        return affiliation != null && companyId.equals(affiliation.companyId())
                && ACTIVE_AFFILIATION.equals(affiliation.affiliationStatus());
    }
}
