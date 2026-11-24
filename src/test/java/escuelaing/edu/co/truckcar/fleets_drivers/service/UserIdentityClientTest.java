package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.sun.net.httpserver.HttpServer;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.IdentityUnavailableException;
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

/** Contra un User-Identity falso: la empresa sale de /companies/me con el mismo token del usuario. */
class UserIdentityClientTest {

    private HttpServer server;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private UserIdentityClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/companies/me", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = (status.get() == 200
                    ? "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"name\":\"Transportes Andinos\"}"
                    : "{\"status\":" + status.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new UserIdentityClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void forwardsTheUserTokenAndReturnsTheCompanyId() {
        assertThat(client.companyIdOf("Bearer abc")).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer abc");
    }

    @Test
    void noCompanyOrWrongRoleIsForbiddenAndAnOutageIsUnavailable() {
        status.set(404);
        assertThatThrownBy(() -> client.companyIdOf("Bearer abc")).isInstanceOf(FleetAccessDeniedException.class);
        status.set(403);
        assertThatThrownBy(() -> client.companyIdOf("Bearer abc")).isInstanceOf(FleetAccessDeniedException.class);
        status.set(503);
        assertThatThrownBy(() -> client.companyIdOf("Bearer abc")).isInstanceOf(IdentityUnavailableException.class);
        assertThatThrownBy(() -> client.companyIdOf(null)).isInstanceOf(FleetAccessDeniedException.class);
    }
}
