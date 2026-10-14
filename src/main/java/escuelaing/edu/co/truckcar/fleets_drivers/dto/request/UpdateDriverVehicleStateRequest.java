package escuelaing.edu.co.truckcar.fleets_drivers.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDriverVehicleStateRequest {

    @NotBlank(message = "El ID del conductor es obligatorio")
    private String driverId;

    private String driverName;
    private String licenseCategory;
    private Double rating;
    private Integer totalTrips;

    @NotBlank(message = "La placa del vehículo es obligatoria")
    private String vehiclePlate;

    private String vehicleModel;
    private String vehicleType;

    @NotNull(message = "La altura máxima es obligatoria para validar gálibo")
    @PositiveOrZero
    private Double maxHeightMeters;

    @PositiveOrZero
    private Double netCargoWeightTons;

    @NotNull(message = "La tara es obligatoria")
    @PositiveOrZero
    private Double tareWeightTons;

    @PositiveOrZero
    private Double drivingHoursToday;

    private String mandatoryRestLocation;

    private boolean hazmatAuthorized;
    private boolean coldChainCertified;
    private boolean runtValid;
    private boolean electronicTollTagActive;
    private List<String> activeBadges;

    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentSpeedKmH;
    private String status;
}