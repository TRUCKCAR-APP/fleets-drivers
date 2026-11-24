package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.fasterxml.jackson.databind.JsonNode;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.IdentityUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * De qué empresa transportadora es dueña la cuenta que hace la petición. Se le pregunta a User-Identity con el mismo
 * token del usuario (GET /api/v1/companies/me): es User-Identity quien valida el token y la propiedad, así que una
 * cuenta no puede hacerse pasar por otra empresa.
 */
@Slf4j
@Component
public class UserIdentityClient {

    private final RestClient restClient;

    public UserIdentityClient(RestClient.Builder builder,
                              @Value("${truckcar.services.user-identity-url:http://localhost:8080}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Id de la empresa de la cuenta TRANSPORTADORA dueña del token. */
    public String companyIdOf(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new FleetAccessDeniedException("Se requiere la sesión de la transportadora para ver su flota");
        }
        JsonNode company;
        try {
            company = restClient.get().uri("/api/v1/companies/me")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new FleetAccessDeniedException("Esta cuenta no tiene una empresa transportadora");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new FleetAccessDeniedException("Solo una cuenta de transportadora puede ver su flota");
        } catch (Exception e) {
            log.warn("No se pudo consultar la empresa en User-Identity: {}", e.getClass().getSimpleName());
            throw new IdentityUnavailableException("No se pudo confirmar su empresa; inténtelo de nuevo en unos segundos");
        }
        String id = company == null ? null : company.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IdentityUnavailableException("User-Identity no informó el id de la empresa");
        }
        return id;
    }
}
