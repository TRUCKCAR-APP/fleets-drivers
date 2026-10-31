package escuelaing.edu.co.truckcar.fleets_drivers.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con el relay activo el contexto arranca aunque RabbitMQ no esté disponible (el relay reconecta solo). Detecta que
 * falte el cliente TCP de Reactor Netty, que de otro modo solo aparece al desplegar con RabbitMQ.
 */
@SpringBootTest(properties = {
        "truckcar.websocket.broker-relay.enabled=true",
        "truckcar.websocket.broker-relay.host=127.0.0.1",
        "truckcar.websocket.broker-relay.port=1"
})
class StompBrokerRelayContextTest {

    @Autowired
    private StompBrokerRelayMessageHandler relayHandler;

    @Test
    void relayStartsWithExternalBrokerConfiguration() {
        assertThat(relayHandler.isRunning()).isTrue();
        assertThat(relayHandler.getRelayPort()).isEqualTo(1);
    }
}
