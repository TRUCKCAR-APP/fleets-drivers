package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.model.OutboxEvent;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.OutboxEventRepository;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    /** Sin Tracer en el contexto: los eventos se guardan sin traceparent. */
    private static final OutboxTracing NO_TRACING =
            new OutboxTracing(new StaticListableBeanFactory().getBeanProvider(Tracer.class));

    @Mock
    private OutboxEventRepository repository;

    @Test
    void enqueuesSerializedAvailabilityEnvelope() {
        OutboxEventService service = new OutboxEventService(repository,
                new ObjectMapper().registerModule(new JavaTimeModule()), NO_TRACING);
        DriverVehicleState state = DriverVehicleState.builder()
                .driverId("driver-1")
                .vehiclePlate("ABC123")
                .currentLatitude(4.7)
                .currentLongitude(-74.1)
                .currentSpeedKmH(45.0)
                .remainingDrivingHours(6.5)
                .status("ACTIVO")
                .lastUpdate(LocalDateTime.of(2026, 10, 10, 19, 0))
                .build();

        service.enqueueAvailabilityEvent(state);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("driver.availability.changed");
        assertThat(event.getAggregateId()).isEqualTo("driver-1");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getPayload()).contains("\"eventType\":\"driver.availability.changed\"");
        assertThat(event.getPayload()).contains("\"driverId\":\"driver-1\"");
    }
}
