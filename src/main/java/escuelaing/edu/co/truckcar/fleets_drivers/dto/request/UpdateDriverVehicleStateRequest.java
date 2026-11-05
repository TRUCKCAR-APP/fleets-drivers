package escuelaing.edu.co.truckcar.fleets_drivers.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

    /** Capacidad de carga del vehículo (licencia de tránsito). Sin ella el motor de asignación no lo propone. */
    @Positive(message = "La capacidad de carga debe ser mayor que 0")
    private Double maxPayloadTons;

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
    /**
     * ACTIVO (recibe cargas) o INACTIVO; sin valor queda ACTIVO. "Disponible", "En tránsito" y "En descanso" son
     * etiquetas que la PWA deriva de ACTIVO, no estados: con otro valor el motor de asignación nunca propondría cargas.
     */
    @Pattern(regexp = "(?i)ACTIVO|INACTIVO",
            message = "El estado debe ser ACTIVO (recibe cargas) o INACTIVO; Disponible y En tránsito se derivan de ACTIVO")
    private String status;
}