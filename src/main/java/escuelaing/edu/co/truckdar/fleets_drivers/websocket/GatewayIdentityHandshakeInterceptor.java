package escuelaing.edu.co.truckdar.fleets_drivers.websocket;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * El handshake de /ws-telemetry solo se acepta con la identidad que inyecta el api-gateway tras validar el JWT
 * (el gateway descarta los X-Auth-* que envíe el cliente). Sin X-Auth-User responde 401.
 */
public class GatewayIdentityHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ATTRIBUTE = "truckdar.userId";
    static final String ROLE_ATTRIBUTE = "truckdar.role";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String userId = request.getHeaders().getFirst("X-Auth-User");
        if (userId == null || userId.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String role = request.getHeaders().getFirst("X-Auth-Role");
        attributes.put(USER_ATTRIBUTE, userId);
        attributes.put(ROLE_ATTRIBUTE, role == null ? "" : role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Sin acciones posteriores.
    }
}
