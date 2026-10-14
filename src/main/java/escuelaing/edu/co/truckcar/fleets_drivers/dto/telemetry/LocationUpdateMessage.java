package escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Posición que envía el conductor a /app/telemetry (STOMP). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateMessage {

    private String driverId;
    private Double latitude;
    private Double longitude;
    /** Velocidad en km/h (opcional). */
    private Double speedKmH;
    /** Rumbo en grados 0-360 (opcional). */
    private Double heading;
    /** Carga en curso; solo se acepta si es una carga activa del conductor. */
    private Long loadId;
}
