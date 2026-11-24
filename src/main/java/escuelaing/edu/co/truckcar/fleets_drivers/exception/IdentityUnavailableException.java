package escuelaing.edu.co.truckcar.fleets_drivers.exception;

/** User-Identity no respondió: sin saber de qué empresa es la cuenta no se puede mostrar su flota (503). */
public class IdentityUnavailableException extends RuntimeException {
    public IdentityUnavailableException(String message) {
        super(message);
    }
}
