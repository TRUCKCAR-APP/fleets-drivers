package escuelaing.edu.co.truckcar.fleets_drivers.exception;

/** La placa ya pertenece al vehículo de otro conductor: dos camiones no pueden tener la misma placa. */
public class PlateAlreadyRegisteredException extends RuntimeException {
    public PlateAlreadyRegisteredException(String message) {
        super(message);
    }
}
