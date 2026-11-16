package escuelaing.edu.co.truckcar.fleets_drivers.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PATCH /drivers/{driverId}/vehicle: solo la ficha técnica del camión. Los campos que no vienen (null) no cambian; el
 * estado operativo (ACTIVO/INACTIVO), la posición, las cargas y las horas de conducción nunca se tocan por aquí.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchVehicleRequest {

    @Size(min = 5, max = 10, message = "La placa debe tener entre 5 y 10 caracteres")
    @Pattern(regexp = "[A-Za-z0-9 -]+", message = "La placa solo admite letras, números, espacios y guiones")
    private String vehiclePlate;

    @Size(max = 120)
    private String vehicleModel;

    @Size(max = 40)
    private String vehicleType;

    @Positive(message = "La altura debe ser mayor que 0")
    @DecimalMax(value = "6.0", message = "La altura no puede superar 6 m")
    private Double maxHeightMeters;

    @Positive(message = "La capacidad de carga debe ser mayor que 0")
    private Double maxPayloadTons;

    @PositiveOrZero(message = "La tara no puede ser negativa")
    private Double tareWeightTons;

    @Size(max = 10)
    private String licenseCategory;

    private Boolean hazmatAuthorized;
    private Boolean coldChainCertified;
    private Boolean runtValid;
    private Boolean electronicTollTagActive;
}
