package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Pregunta a Load-Allocation si un GENERADOR es dueño de una carga: GET /api/v1/loads/{id} con su identidad
 * responde 200 solo para sus propias cargas (403 para las de otras empresas). Así no se duplica la regla.
 */
@Slf4j
@Component
public class LoadOwnershipClient {

    private final RestClient restClient;

    /** Builder autoconfigurado: la consulta propaga traceparent y X-Correlation-Id y se mide (http.client.requests). */
    public LoadOwnershipClient(RestClient.Builder builder,
                               @Value("${truckcar.services.load-allocation-url:http://localhost:8083}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean isOwnedByGenerator(Long loadId, String generatorUserId) {
        try {
            restClient.get()
                    .uri("/api/v1/loads/{id}", loadId)
                    .header("X-Auth-User", generatorUserId)
                    .header("X-Auth-Role", "GENERADOR")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            return false;
        } catch (Exception e) {
            // Sin respuesta de Load-Allocation no se concede la suscripción.
            log.warn("No se pudo verificar la propiedad de la carga #{}: {}", loadId, e.getMessage());
            return false;
        }
    }
}
