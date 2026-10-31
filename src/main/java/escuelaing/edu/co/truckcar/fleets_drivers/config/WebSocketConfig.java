package escuelaing.edu.co.truckcar.fleets_drivers.config;

import escuelaing.edu.co.truckcar.fleets_drivers.websocket.GatewayIdentityHandshakeInterceptor;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryChannelInterceptor;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryHandshakeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Telemetría GPS en tiempo real: STOMP sobre WebSocket en /ws-telemetry (detrás del api-gateway).
 * <ul>
 *   <li>Los conductores envían su posición a /app/telemetry.</li>
 *   <li>La torre de control recibe /topic/fleet-locations; el seguimiento de una carga, /topic/trip.{loadId}.</li>
 *   <li>Los rechazos de cada sesión llegan a /user/queue/errors.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TelemetryChannelInterceptor channelInterceptor;
    private final String[] allowedOriginPatterns;
    private final BrokerRelayProperties relay;

    public WebSocketConfig(TelemetryChannelInterceptor channelInterceptor,
                           @Value("${truckcar.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
                           String[] allowedOriginPatterns,
                           BrokerRelayProperties relay) {
        this.channelInterceptor = channelInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
        this.relay = relay;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-telemetry")
                .setHandshakeHandler(new TelemetryHandshakeHandler())
                .addInterceptors(new GatewayIdentityHandshakeInterceptor())
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * Con el broker simple las suscripciones viven en la memoria de esta instancia: una posición recibida por una réplica
     * no llega a los suscriptores conectados a otra. Con truckcar.websocket.broker-relay.enabled=true los mensajes pasan
     * por RabbitMQ (plugin STOMP) y fleets-drivers puede escalar horizontalmente. La autorización de SUBSCRIBE y SEND
     * sigue en TelemetryChannelInterceptor, antes de llegar al broker.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (relay.isEnabled()) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(relay.getHost())
                    .setRelayPort(relay.getPort())
                    .setVirtualHost(relay.getVirtualHost())
                    .setClientLogin(relay.getLogin())
                    .setClientPasscode(relay.getPasscode())
                    .setSystemLogin(relay.getLogin())
                    .setSystemPasscode(relay.getPasscode())
                    // Heartbeats de 10 s con RabbitMQ: detectan una conexión caída y el relay reconecta.
                    .setSystemHeartbeatSendInterval(10_000)
                    .setSystemHeartbeatReceiveInterval(10_000)
                    // Con varias réplicas, /user/queue/errors llega a la sesión aunque esté en otra instancia.
                    .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                    .setUserRegistryBroadcast("/topic/simp-user-registry");
        } else {
            ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
            heartbeatScheduler.setPoolSize(1);
            heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
            heartbeatScheduler.initialize();
            // Heartbeats de 10 s: mantienen viva la conexión a través del gateway y detectan clientes caídos.
            registry.enableSimpleBroker("/topic", "/queue")
                    .setHeartbeatValue(new long[]{10_000, 10_000})
                    .setTaskScheduler(heartbeatScheduler);
        }
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }
}
