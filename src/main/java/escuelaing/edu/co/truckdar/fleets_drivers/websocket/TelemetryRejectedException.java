package escuelaing.edu.co.truckdar.fleets_drivers.websocket;

/** Posición rechazada por permisos o datos inválidos; se informa a /user/queue/errors. */
public class TelemetryRejectedException extends RuntimeException {

    public TelemetryRejectedException(String message) {
        super(message);
    }
}
