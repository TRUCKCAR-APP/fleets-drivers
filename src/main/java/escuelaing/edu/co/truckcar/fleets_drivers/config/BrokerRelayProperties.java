package escuelaing.edu.co.truckcar.fleets_drivers.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Broker STOMP externo (RabbitMQ con rabbitmq_stomp) para la telemetría. Desactivado usa el broker simple en memoria,
 * válido solo con una réplica de fleets-drivers.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "truckcar.websocket.broker-relay")
public class BrokerRelayProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 61613;
    private String virtualHost = "/";
    private String login = "guest";
    private String passcode = "guest";
}
