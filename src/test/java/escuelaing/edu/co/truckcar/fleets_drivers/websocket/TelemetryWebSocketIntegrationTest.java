package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Conexión STOMP real contra /ws-telemetry: handshake con la identidad del gateway, permisos de publicación y de
 * suscripción, y retransmisión a la torre de control. Los listeners de Kafka no arrancan para no consumir los
 * eventos del entorno local.
 * <p>
 * Un frame rechazado hace que el servidor envíe ERROR y cierre la sesión. Spring puede perder ese ERROR: si otro hilo
 * está enviando a la sesión, ConcurrentWebSocketSessionDecorator lo deja en su buffer y el cierre inmediato lo descarta.
 * Por eso los rechazos se comprueban con lo que siempre ocurre (el cierre de la sesión y el motivo en el log del
 * servidor, que se escribe antes de responder) y el texto del ERROR solo cuando llegó, que es antes del cierre porque
 * ambos viajan en orden por la misma conexión.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.listener.auto-startup=false")
class TelemetryWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    private IFleetDriverService fleetDriverService;

    @MockBean
    private LoadOwnershipClient loadOwnershipClient;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(session -> {
            try {
                session.disconnect();
            } catch (RuntimeException alreadyClosed) {
                // El servidor cerró la sesión (rechazo) entre la prueba y el cierre: no queda nada que desconectar.
            }
        });
        stompClient.stop();
    }

    /** Conecta como lo haría el api-gateway tras validar el JWT; errors recibe el mensaje de un frame ERROR. */
    private StompSession connect(String userId, String role, CompletableFuture<String> errors) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        if (userId != null) {
            handshake.add("X-Auth-User", userId);
            handshake.add("X-Auth-Role", role);
        }
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws-telemetry", handshake,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        if (errors != null) errors.complete(headers.getFirst("message"));
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }
                }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private static CompletableFuture<Map<String, Object>> subscribe(StompSession session, String destination) {
        CompletableFuture<Map<String, Object>> received = new CompletableFuture<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((Map<String, Object>) payload);
            }
        });
        return received;
    }

    /**
     * Espera de forma determinista el rechazo del servidor: la sesión siempre termina cerrada y el motivo siempre queda
     * en el log. Si el ERROR llegó (antes del cierre), también debe traer el motivo.
     */
    private static void assertRejected(StompSession session, CompletableFuture<String> errorFrame, CapturedOutput output,
                                       String logReason, String errorReason) {
        await().atMost(Duration.ofSeconds(10)).until(() -> !session.isConnected());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut()).contains(logReason));
        if (errorFrame.isDone()) {
            assertThat(errorFrame.join()).contains(errorReason);
        }
    }

    private static LocationUpdateMessage position(String driverId) {
        return LocationUpdateMessage.builder().driverId(driverId).latitude(4.61).longitude(-74.08)
                .speedKmH(62.0).heading(210.0).loadId(30L).build();
    }

    @Test
    void driverPositionIsBroadcastToTheControlTowerAndTheTripTopic() throws Exception {
        when(fleetDriverService.recordLocation(any())).thenReturn(FleetLocationMessage.builder()
                .driverId("ana").vehiclePlate("TDK-410").latitude(4.61).longitude(-74.08).speedKmH(62.0)
                .heading(210.0).loadId(30L).loadStatus("IN_TRANSIT").timestamp(Instant.now()).build());
        StompSession tower = connect("coord-1", "COORDINADOR", null);
        CompletableFuture<Map<String, Object>> fleet = subscribe(tower, "/topic/fleet-locations");
        CompletableFuture<Map<String, Object>> trip = subscribe(tower, "/topic/trip.30");
        Thread.sleep(300);

        connect("ana", "CONDUCTOR", null).send("/app/telemetry", position("ana"));

        Map<String, Object> received = fleet.get(5, TimeUnit.SECONDS);
        assertThat(received).containsEntry("driverId", "ana").containsEntry("vehiclePlate", "TDK-410");
        assertThat(received.get("latitude")).isEqualTo(4.61);
        assertThat(trip.get(5, TimeUnit.SECONDS)).containsEntry("loadStatus", "IN_TRANSIT");
    }

    @Test
    void driverCannotPublishAnotherDriversPosition() throws Exception {
        StompSession ana = connect("ana", "CONDUCTOR", null);
        CompletableFuture<Map<String, Object>> errors = subscribe(ana, "/user/queue/errors");
        Thread.sleep(300);

        ana.send("/app/telemetry", position("otro-conductor"));

        assertThat(errors.get(5, TimeUnit.SECONDS)).containsEntry("error", "TELEMETRY_REJECTED");
        verify(fleetDriverService, never()).recordLocation(any());
    }

    @Test
    void invalidCoordinatesAreRejected() throws Exception {
        StompSession ana = connect("ana", "CONDUCTOR", null);
        CompletableFuture<Map<String, Object>> errors = subscribe(ana, "/user/queue/errors");
        Thread.sleep(300);

        LocationUpdateMessage invalid = position("ana");
        invalid.setLatitude(123.0);
        ana.send("/app/telemetry", invalid);

        assertThat(String.valueOf(errors.get(5, TimeUnit.SECONDS).get("message"))).contains("Coordenadas inválidas");
    }

    @Test
    void generatorCannotSubscribeToTheWholeFleet(CapturedOutput output) throws Exception {
        CompletableFuture<String> serverError = new CompletableFuture<>();
        StompSession generator = connect("gen-1", "GENERADOR", serverError);

        CompletableFuture<Map<String, Object>> fleet = subscribe(generator, "/topic/fleet-locations");

        assertRejected(generator, serverError, output,
                "Suscripción rechazada a /topic/fleet-locations para gen-1 (GENERADOR)", "No tiene permiso");
        assertThat(fleet).isNotDone();
    }

    @Test
    void owningGeneratorFollowsItsTrip() throws Exception {
        when(loadOwnershipClient.isOwnedByGenerator(30L, "gen-1")).thenReturn(true);
        CompletableFuture<String> serverError = new CompletableFuture<>();
        StompSession generator = connect("gen-1", "GENERADOR", serverError);

        subscribe(generator, "/topic/trip.30");

        verify(loadOwnershipClient, timeout(3000)).isOwnedByGenerator(30L, "gen-1");
        Thread.sleep(300);
        assertThat(serverError).isNotDone();
        assertThat(generator.isConnected()).isTrue();
    }

    @Test
    void clientsCannotPublishDirectlyToBrokerTopics(CapturedOutput output) throws Exception {
        CompletableFuture<String> serverError = new CompletableFuture<>();
        StompSession ana = connect("ana", "CONDUCTOR", serverError);

        ana.send("/topic/fleet-locations", position("ana"));

        assertRejected(ana, serverError, output,
                "Publicación rechazada a /topic/fleet-locations para ana (CONDUCTOR)", "/app/telemetry");
        verify(fleetDriverService, never()).recordLocation(any());
    }

    @Test
    void handshakeWithoutGatewayIdentityIsRefused() {
        assertThatThrownBy(() -> connect(null, null, null)).rootCause().hasMessageContaining("401");
    }
}
