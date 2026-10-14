package escuelaing.edu.co.truckcar.fleets_drivers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverVehicleStateResponse {

    private String driverId;
    private String driverName;
    private String licenseCategory;
    private Double rating;
    private Integer totalTrips;

    private String vehiclePlate;
    private String vehicleModel;
    private String vehicleType;

    private Double maxHeightMeters;
    private Double netCargoWeightTons;
    private Double tareWeightTons;
    private Double grossWeightTons;

    private Double drivingHoursToday;
    private Double maxDailyDrivingHours;
    private Double remainingDrivingHours;
    private String mandatoryRestLocation;
    private boolean restRequiredAlert;

    private boolean hazmatAuthorized;
    private boolean coldChainCertified;
    private boolean runtValid;
    private boolean electronicTollTagActive;
    private List<String> activeBadges;

    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentSpeedKmH;
    /** Rumbo en grados (0 = norte, sentido horario), reportado por la telemetría GPS. */
    private Double currentHeading;
    private String status;
    private LocalDateTime lastUpdate;

    /** Carga en curso del conductor (IN_TRANSIT tiene prioridad sobre ASSIGNED); null si está libre. */
    private Long currentLoadId;
    private String currentLoadStatus;
    /** true si tiene una carga aceptada o en ruta: no debe recibir otra. */
    private boolean onTrip;
}