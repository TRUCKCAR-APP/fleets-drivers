package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

/** Posición rechazada por permisos o datos inválidos; se informa a /user/queue/errors. */
public class TelemetryRejectedException extends RuntimeException {

    public TelemetryRejectedException(String message) {
        super(message);
    }
}
