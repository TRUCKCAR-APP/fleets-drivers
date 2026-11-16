package escuelaing.edu.co.truckcar.fleets_drivers.exception;

/** Load-Allocation no respondió: sin sus datos no se puede decir quién es elegible para una carga (503). */
public class LoadAllocationUnavailableException extends RuntimeException {
    public LoadAllocationUnavailableException(String message) {
        super(message);
    }
}
