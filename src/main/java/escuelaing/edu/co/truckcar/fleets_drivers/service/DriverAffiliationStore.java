package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tipo de conductor y afiliación a una transportadora según user-identity (evento user.affiliation.changed), en el
 * hash de Redis driver_affiliation. Un independiente o un conductor de empresa sin la afiliación ACTIVA no recibe
 * cargas (loadEligible=false). Un conductor del que nunca llegó el evento (cuentas anteriores a la afiliación) conserva
 * el comportamiento de antes: puede recibir cargas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAffiliationStore {

    static final String KEY = "driver_affiliation";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** Afiliación conocida de un conductor. */
    public record Affiliation(String driverType, String companyId, String companyName, String affiliationStatus,
                              boolean loadEligible) {
    }

    public void put(String driverId, Affiliation affiliation) {
        try {
            redis.opsForHash().put(KEY, driverId, objectMapper.writeValueAsString(affiliation));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la afiliación de " + driverId, e);
        }
    }

    /** Afiliación del conductor, o null si user-identity no la ha informado. */
    public Affiliation get(String driverId) {
        Object raw = redis.opsForHash().get(KEY, driverId);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw.toString(), Affiliation.class);
        } catch (JsonProcessingException e) {
            log.warn("Afiliación ilegible para {}: se ignora", driverId);
            return null;
        }
    }

    /** Si la afiliación le permite recibir cargas (sin dato: sí, como antes de la afiliación). */
    public boolean allowsLoads(String driverId) {
        Affiliation affiliation = get(driverId);
        return affiliation == null || affiliation.loadEligible();
    }
}
