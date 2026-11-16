package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.fasterxml.jackson.databind.JsonNode;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.LoadAllocationUnavailableException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Lo que Load-Allocation sabe de una carga y no tiene fleets-drivers: quién la rechazó o la dejó vencer y qué
 * conductores tienen ya una propuesta vigente o una carga aceptada. Son llamadas internas sin identidad (las dos rutas
 * las admiten para los servicios; por el gateway siempre llevan X-Auth-User).
 */
@Slf4j
@Component
public class LoadAllocationClient {

    private final RestClient restClient;

    /** Builder autoconfigurado: propaga traceparent y X-Correlation-Id y se mide (http.client.requests). */
    public LoadAllocationClient(RestClient.Builder builder,
                                @Value("${truckcar.services.load-allocation-url:http://localhost:8083}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Datos de la carga que deciden quién puede recibirla. */
    public record LoadSnapshot(Long id, String requiredTruckType, Double weightTons, Set<String> discardedDriverIds) {
    }

    public LoadSnapshot load(Long loadId) {
        JsonNode load = get("/api/v1/loads/" + loadId, "la carga #" + loadId);
        Set<String> discarded = new HashSet<>();
        load.path("discardedDriverIds").forEach(id -> discarded.add(id.asText()));
        JsonNode weight = load.get("weightTons");
        return new LoadSnapshot(loadId, text(load, "requiredTruckType"),
                weight != null && weight.isNumber() ? weight.asDouble() : null, Set.copyOf(discarded));
    }

    /** Conductores con una propuesta vigente o una carga aceptada o en ruta (cualquier carga). */
    public Set<String> committedDriverIds() {
        JsonNode commitments = get("/api/v1/loads/driver-commitments", "la ocupación de los conductores");
        Set<String> ids = new HashSet<>();
        commitments.forEach(c -> {
            String id = text(c, "driverId");
            if (id != null) {
                ids.add(id);
            }
        });
        return Set.copyOf(ids);
    }

    private JsonNode get(String path, String what) {
        try {
            JsonNode body = restClient.get().uri(path).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new LoadAllocationUnavailableException("Load-Allocation respondió vacío al consultar " + what);
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("No se encontró " + what);
        } catch (LoadAllocationUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo consultar {} en Load-Allocation: {}", what, e.getClass().getSimpleName());
            throw new LoadAllocationUnavailableException("No se pudo consultar " + what
                    + " en Load-Allocation; inténtelo de nuevo en unos segundos");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
