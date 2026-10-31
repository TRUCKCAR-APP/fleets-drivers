package escuelaing.edu.co.truckcar.fleets_drivers.sync;

import com.fasterxml.jackson.databind.JsonNode;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.PositionHistory;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryAccessPolicy;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryController;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.TelemetryRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Posiciones GPS que la PWA registró sin señal. Todas se guardan en el histórico (en orden cronológico y sin
 * duplicados); por WebSocket solo se reemite la más reciente, y solo si es más nueva que el estado vivo: el mapa no
 * reproduce la ráfaga ni hace retroceder al camión.
 */
@Slf4j
@Service
public class FleetSyncService {

    public static final String POSITION = "position";

    private static final Comparator<SyncBatchRequest.Event> CHRONOLOGICAL = Comparator
            .comparing(SyncBatchRequest.Event::getOccurredAt)
            .thenComparing(event -> event.getSeq() == null ? 0L : event.getSeq());

    private final IngestionLedger ledger;
    private final PositionHistory history;
    private final IFleetDriverService fleetDriverService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transaction;

    public FleetSyncService(IngestionLedger ledger, PositionHistory history, IFleetDriverService fleetDriverService,
                            SimpMessagingTemplate messagingTemplate, PlatformTransactionManager transactionManager) {
        this.ledger = ledger;
        this.history = history;
        this.fleetDriverService = fleetDriverService;
        this.messagingTemplate = messagingTemplate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public SyncBatchResponse process(String driverId, SyncBatchRequest request) {
        SyncBatchResponse response = new SyncBatchResponse();
        List<SyncBatchRequest.Event> ordered = request.getEvents().stream().sorted(CHRONOLOGICAL).toList();
        SyncBatchRequest.Event latest = null;
        LocationUpdateMessage latestUpdate = null;
        for (SyncBatchRequest.Event event : ordered) {
            LocationUpdateMessage update;
            try {
                update = positionOf(driverId, event);
            } catch (IllegalArgumentException | TelemetryRejectedException invalid) {
                // Rechazo definitivo: se registra para que el reenvío del lote no lo vuelva a evaluar.
                if (ledger.register(driverId, event, IngestionLedger.REJECTED, invalid.getMessage())) {
                    response.getRejected().add(new SyncBatchResponse.Rejected(event.getClientEventId(), invalid.getMessage()));
                } else {
                    response.getDuplicates().add(event.getClientEventId());
                }
                continue;
            }
            // Registro e histórico en la misma transacción: o quedan ambos o ninguno.
            Boolean fresh = transaction.execute(status -> {
                if (!ledger.register(driverId, event, IngestionLedger.APPLIED, null)) {
                    return false;
                }
                history.record(update, event.getOccurredAt(), PositionHistory.OFFLINE, event.getClientEventId());
                return true;
            });
            if (Boolean.TRUE.equals(fresh)) {
                response.getAccepted().add(event.getClientEventId());
                latest = event;
                latestUpdate = update;
            } else {
                response.getDuplicates().add(event.getClientEventId());
            }
        }
        if (latest != null) {
            publishIfNewest(latestUpdate, latest);
        }
        log.info("Lote offline del conductor {}: {} posiciones nuevas, {} duplicadas, {} rechazadas", driverId,
                response.getAccepted().size(), response.getDuplicates().size(), response.getRejected().size());
        return response;
    }

    private void publishIfNewest(LocationUpdateMessage update, SyncBatchRequest.Event event) {
        Optional<FleetLocationMessage> live;
        try {
            live = fleetDriverService.recordLocationAt(update, event.getOccurredAt());
        } catch (ResourceNotFoundException noVehicle) {
            log.info("Conductor {} sin vehículo registrado: el histórico se guarda sin actualizar el mapa",
                    update.getDriverId());
            return;
        }
        live.ifPresent(location -> {
            messagingTemplate.convertAndSend(TelemetryAccessPolicy.FLEET_TOPIC, location);
            if (location.getLoadId() != null) {
                messagingTemplate.convertAndSend(TelemetryAccessPolicy.TRIP_TOPIC_PREFIX + location.getLoadId(), location);
            }
        });
    }

    /** El conductor es siempre el autenticado: el payload no puede publicar la posición de otro. */
    private static LocationUpdateMessage positionOf(String driverId, SyncBatchRequest.Event event) {
        if (!POSITION.equals(event.getKind())) {
            throw new IllegalArgumentException("Tipo de evento no admitido por fleets-drivers: " + event.getKind());
        }
        JsonNode payload = event.getPayload();
        LocationUpdateMessage update = LocationUpdateMessage.builder()
                .driverId(driverId)
                .latitude(number(payload, "latitude"))
                .longitude(number(payload, "longitude"))
                .speedKmH(number(payload, "speedKmH"))
                .heading(number(payload, "heading"))
                .loadId(payload.path("loadId").canConvertToLong() ? payload.path("loadId").asLong() : null)
                .build();
        TelemetryController.validate(update);
        return update;
    }

    private static Double number(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asDouble();
    }
}
