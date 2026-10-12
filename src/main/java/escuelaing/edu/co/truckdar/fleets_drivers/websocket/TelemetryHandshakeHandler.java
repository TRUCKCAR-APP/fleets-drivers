package escuelaing.edu.co.truckdar.fleets_drivers.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/** Asocia a la sesión STOMP el usuario que dejó el interceptor del handshake. */
public class TelemetryHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get(GatewayIdentityHandshakeInterceptor.USER_ATTRIBUTE);
        Object role = attributes.get(GatewayIdentityHandshakeInterceptor.ROLE_ATTRIBUTE);
        return userId == null ? null : new TelemetryPrincipal(userId.toString(), role == null ? "" : role.toString());
    }
}
