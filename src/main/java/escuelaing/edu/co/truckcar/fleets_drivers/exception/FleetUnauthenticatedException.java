package escuelaing.edu.co.truckcar.fleets_drivers.exception;

/** La consulta exige la identidad que propaga el api-gateway (X-Auth-User y X-Auth-Role) y no llegó (401). */
public class FleetUnauthenticatedException extends RuntimeException {

    public FleetUnauthenticatedException(String message) {
        super(message);
    }
}
