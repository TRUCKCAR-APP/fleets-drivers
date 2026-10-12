package escuelaing.edu.co.truckdar.fleets_drivers.websocket;

import java.security.Principal;

/** Usuario de una sesión STOMP: id y rol que el api-gateway extrae del JWT (X-Auth-User / X-Auth-Role). */
public record TelemetryPrincipal(String userId, String role) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
