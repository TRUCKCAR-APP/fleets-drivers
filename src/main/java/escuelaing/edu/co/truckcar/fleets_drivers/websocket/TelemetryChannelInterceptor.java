package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Filtra los frames STOMP que llegan de los clientes:
 * <ul>
 *   <li>CONNECT exige la identidad del handshake.</li>
 *   <li>SUBSCRIBE aplica {@link TelemetryAccessPolicy#canSubscribe}.</li>
 *   <li>SEND solo a /app/** (el controlador valida y retransmite). Un SEND directo a /topic/** permitiría publicar
 *       posiciones falsas sin pasar por las reglas, así que se rechaza.</li>
 * </ul>
 * Un frame rechazado provoca un ERROR STOMP y el cierre de la sesión.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryChannelInterceptor implements ChannelInterceptor {

    private final TelemetryAccessPolicy accessPolicy;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        TelemetryPrincipal principal = accessor.getUser() instanceof TelemetryPrincipal user ? user : null;
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();
        if (command == StompCommand.CONNECT && principal == null) {
            throw new MessagingException("Sesión sin identidad: conéctese a través del api-gateway");
        }
        if (command == StompCommand.SUBSCRIBE && !accessPolicy.canSubscribe(principal, destination)) {
            log.info("Suscripción rechazada a {} para {} ({})", destination,
                    principal == null ? "anónimo" : principal.userId(), principal == null ? "-" : principal.role());
            throw new MessagingException("No tiene permiso para suscribirse a " + destination);
        }
        if (command == StompCommand.SEND && (destination == null || !destination.startsWith("/app/"))) {
            throw new MessagingException("Solo se pueden enviar mensajes a /app/telemetry");
        }
        return message;
    }
}
