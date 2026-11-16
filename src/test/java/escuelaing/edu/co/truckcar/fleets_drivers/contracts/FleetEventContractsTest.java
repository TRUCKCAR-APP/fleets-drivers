package escuelaing.edu.co.truckcar.fleets_drivers.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import escuelaing.edu.co.truckcar.fleets_drivers.listener.LoadLifecycleEventListener;
import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.model.OutboxEvent;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.OutboxEventRepository;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.OutboxEventService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.OutboxTracing;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.LoadStatusBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contratos de eventos de fleets-drivers, con el mismo ObjectMapper que arma Spring Boot:
 * <ul>
 *   <li>Productor de driver.availability.changed (lo consume AI-fleet): fixture fleets-drivers.driver-availability.v1.json.</li>
 *   <li>Consumidor de los eventos de carga de Load-Allocation: copia del fixture que Load-Allocation verifica contra
 *   su outbox, con campos que fleets-drivers no conoce, también a través de un Kafka real (Testcontainers).</li>
 * </ul>
 */
class FleetEventContractsTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private JsonNode fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contracts/" + name)) {
            return mapper.readTree(in);
        }
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(keys::add);
        return keys;
    }

    private String loadEvent(String loadStatus) throws Exception {
        ObjectNode event = (ObjectNode) fixture("load-allocation.load-event.v1.json");
        event.put("eventType", "assignment.accepted").put("campoFuturoDelSobre", "x");
        ((ObjectNode) event.get("payload")).put("loadStatus", loadStatus).put("campoFuturo", 1);
        return mapper.writeValueAsString(event);
    }

    @Test
    void theAvailabilityEventHasExactlyTheAgreedShape() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        when(repository.save(saved.capture())).thenAnswer(i -> i.getArgument(0));
        DriverVehicleState state = DriverVehicleState.builder().driverId("ana").vehiclePlate("TDK-410")
                .currentLatitude(4.711).currentLongitude(-74.0721).currentSpeedKmH(60.0).remainingDrivingHours(8.5)
                .status("ACTIVO").lastUpdate(LocalDateTime.of(2026, 11, 16, 13, 0)).build();

        new OutboxEventService(repository, mapper, mock(OutboxTracing.class)).enqueueAvailabilityEvent(state);

        JsonNode published = mapper.readTree(saved.getValue().getPayload());
        JsonNode agreed = fixture("fleets-drivers.driver-availability.v1.json");
        assertThat(keys(published)).as("sobre").isEqualTo(keys(agreed));
        assertThat(keys(published.get("payload"))).as("payload").isEqualTo(keys(agreed.get("payload")));
    }

    @Test
    void aLoadEventWithFieldsWeDoNotKnowStillUpdatesTheDriversActiveLoads() throws Exception {
        IFleetDriverService service = mock(IFleetDriverService.class);
        LoadStatusBroadcaster broadcaster = mock(LoadStatusBroadcaster.class);

        new LoadLifecycleEventListener(service, mapper, broadcaster).handleLoadEvent(loadEvent("ASSIGNED"));

        verify(service).applyLoadStatus("driver-1", 95L, "ASSIGNED", 20.0);
        verify(broadcaster).broadcast(any());
    }

    /** La cadena de carga por un broker real, con la configuración de producción (StringDeserializer). */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class ThroughARealKafka {

        @Container
        static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

        @Test
        void loadAllocationsEventCrossesKafkaAndUpdatesTheDriver() throws Exception {
            String topic = "truckcar.events.load-allocation";
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
                producer.send(new ProducerRecord<>(topic, "95", loadEvent("IN_TRANSIT"))).get();
            }
            IFleetDriverService service = mock(IFleetDriverService.class);
            LoadLifecycleEventListener listener = new LoadLifecycleEventListener(service, mapper, mock(LoadStatusBroadcaster.class));
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                    ConsumerConfig.GROUP_ID_CONFIG, "fleets-drivers-group",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
                consumer.subscribe(List.of(topic));
                ConsumerRecords<String, String> records = ConsumerRecords.empty();
                for (int i = 0; i < 20 && records.isEmpty(); i++) {
                    records = consumer.poll(Duration.ofMillis(500));
                }
                assertThat(records.count()).isEqualTo(1);
                records.forEach(record -> listener.handleLoadEvent(record.value()));
            }
            verify(service).applyLoadStatus("driver-1", 95L, "IN_TRANSIT", 20.0);
        }
    }
}
