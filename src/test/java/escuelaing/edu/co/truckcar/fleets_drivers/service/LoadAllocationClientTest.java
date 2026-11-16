package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.sun.net.httpserver.HttpServer;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.LoadAllocationUnavailableException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra un Load-Allocation falso (servidor HTTP del JDK). */
class LoadAllocationClientTest {

    private HttpServer server;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> lastAuthUser = new AtomicReference<>("sin-consultar");
    private LoadAllocationClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/loads/", exchange -> {
            lastAuthUser.set(exchange.getRequestHeaders().getFirst("X-Auth-User"));
            String path = exchange.getRequestURI().getPath();
            String body = path.endsWith("/driver-commitments")
                    ? "[{\"driverId\":\"ana\",\"loadId\":7,\"loadStatus\":\"AVAILABLE\",\"assignmentStatus\":\"NOTIFIED\"},"
                      + "{\"driverId\":\"luis\",\"loadId\":8,\"loadStatus\":\"ASSIGNED\",\"assignmentStatus\":\"ACCEPTED\"}]"
                    : "{\"id\":12,\"status\":\"AVAILABLE\",\"requiredTruckType\":\"TRACTOMULA_3S3\",\"weightTons\":30.5,"
                      + "\"discardedDriverIds\":[\"pedro\",\"maria\"]}";
            byte[] bytes = (status.get() == 200 ? body : "{\"status\":" + status.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new LoadAllocationClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsTheLoadRequirementsAndWhoDiscardedIt() {
        LoadAllocationClient.LoadSnapshot load = client.load(12L);

        assertThat(load.requiredTruckType()).isEqualTo("TRACTOMULA_3S3");
        assertThat(load.weightTons()).isEqualTo(30.5);
        assertThat(load.discardedDriverIds()).containsExactlyInAnyOrder("pedro", "maria");
        assertThat(lastAuthUser.get()).as("llamada interna, sin identidad").isNull();
    }

    @Test
    void readsEveryDriverWithAPendingProposalOrAnAcceptedLoad() {
        assertThat(client.committedDriverIds()).containsExactlyInAnyOrder("ana", "luis");
    }

    @Test
    void anUnknownLoadIsNotFound() {
        status.set(404);

        assertThatThrownBy(() -> client.load(99L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("#99");
    }

    @Test
    void aFailingOrUnreachableLoadAllocationIsUnavailable() {
        status.set(500);
        assertThatThrownBy(() -> client.committedDriverIds()).isInstanceOf(LoadAllocationUnavailableException.class);

        LoadAllocationClient unreachable = new LoadAllocationClient(RestClient.builder(), "http://127.0.0.1:1");
        assertThatThrownBy(() -> unreachable.load(12L)).isInstanceOf(LoadAllocationUnavailableException.class)
                .hasMessageContaining("inténtelo de nuevo");
    }
}
