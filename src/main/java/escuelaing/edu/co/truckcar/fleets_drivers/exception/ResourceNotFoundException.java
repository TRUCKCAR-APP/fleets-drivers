package escuelaing.edu.co.truckcar.fleets_drivers.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}