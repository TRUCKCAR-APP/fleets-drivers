package escuelaing.edu.co.truckcar.fleets_drivers.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Estado de la cuenta de cada conductor según user-identity (ACTIVE, PENDING_VERIFICATION o SUSPENDED), guardado en
 * el hash de Redis driver_account_status. Se llena con user.created y user.status.changed: fleets-drivers no lee la
 * base de user-identity. Solo una cuenta ACTIVE (verificada por un administrador) puede recibir cargas; un conductor
 * del que aún no llegó ningún evento cuenta como no verificado.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusStore {

    static final String KEY = "driver_account_status";
    public static final String VERIFIED = "ACTIVE";

    private final StringRedisTemplate redis;

    public void put(String driverId, String status) {
        redis.opsForHash().put(KEY, driverId, status);
    }

    /** Estado conocido de la cuenta, o null si user-identity aún no lo informó. */
    public String get(String driverId) {
        Object status = redis.opsForHash().get(KEY, driverId);
        return status == null ? null : status.toString();
    }

    public boolean isVerified(String driverId) {
        return VERIFIED.equals(get(driverId));
    }
}
