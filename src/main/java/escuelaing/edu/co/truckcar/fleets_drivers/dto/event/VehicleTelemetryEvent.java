package escuelaing.edu.co.truckcar.fleets_drivers.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTelemetryEvent {
    private String driverId;
    private String vehiclePlate;
    private Double latitude;
    private Double longitude;
    private Double speedKmH;
    private Double remainingDrivingHours;
    private String status;
    private LocalDateTime timestamp;
}