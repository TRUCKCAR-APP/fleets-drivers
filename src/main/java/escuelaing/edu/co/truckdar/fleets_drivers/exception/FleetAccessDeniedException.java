package escuelaing.edu.co.truckdar.fleets_drivers.exception;

/** El rol del usuario no puede ver la flota completa (403). */
public class FleetAccessDeniedException extends RuntimeException {

    public FleetAccessDeniedException(String message) {
        super(message);
    }
}
