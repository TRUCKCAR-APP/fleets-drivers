package escuelaing.edu.co.truckdar.fleets_drivers.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}