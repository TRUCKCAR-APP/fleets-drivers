package escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Posición retransmitida a /topic/fleet-locations y /topic/trip/{loadId}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetLocationMessage {

    private String driverId;
    private String driverName;
    private String vehiclePlate;
    private Double latitude;
    private Double longitude;
    private Double speedKmH;
    private Double heading;
    /** Carga en curso validada contra las cargas activas del conductor; null si no aplica. */
    private Long loadId;
    private String loadStatus;
    private Instant timestamp;
}
