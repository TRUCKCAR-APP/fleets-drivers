package escuelaing.edu.co.truckdar.fleets_drivers.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("driver_vehicle_state")
public class DriverVehicleState implements Serializable {

    @Id
    private String driverId;

    private String driverName;
    private String licenseCategory;
    private Double rating;
    private Integer totalTrips;

    @Indexed
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

    /** Cargas activas del conductor (loadId -> ASSIGNED | IN_TRANSIT), derivadas de los eventos de Load-Allocation. */
    @Builder.Default
    private Map<String, String> activeLoads = new HashMap<>();
}