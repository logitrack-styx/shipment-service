
package com.logitrack.shipment.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.logitrack.shipment.config.JpaAuditingConfig;
import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.model.Direccion;
import com.logitrack.shipment.model.Envio;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, com.logitrack.shipment.service.impl.AssignmentRequestService.class,
        com.logitrack.shipment.service.impl.AssignmentResultService.class})
@Testcontainers
class EnvioRepositoryTest {

    @Container
    static final MySQLContainer mysql =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("logitrack_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @Autowired
    private EnvioRepository envioRepository;
    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private com.logitrack.shipment.service.impl.AssignmentRequestService requests;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private com.logitrack.shipment.service.impl.AssignmentResultService results;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void resultCannotOverwriteLegacyAssignmentOrReopenTerminalOperation() {
        var id = envio.getId();
        var operation = requests.submit(id, repartidorId, UUID.randomUUID());
        var legacyCourier = UUID.randomUUID();
        assertEquals(1, envioRepository.asignarRepartidorSiPendienteSinAsignacion(id, legacyCourier));
        var event = new com.logitrack.shipment.event.AssignmentEvent(
                UUID.randomUUID(), operation.operationId(), Instant.now(), 1, id, repartidorId);
        results.apply(event, true);
        results.apply(event, true);
        assertEquals("REJECTED", requests.get(id, operation.operationId()).status().name());
        assertEquals(legacyCourier, envioRepository.findById(id).orElseThrow().getRepartidorId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM processed_assignment_results WHERE operation_id = ?",
                Integer.class, operation.operationId().toString()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesPersistOneOperationAndOneEvent() throws Exception {
        var key = UUID.randomUUID();
        var id = envio.getId();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            java.util.concurrent.Callable<com.logitrack.shipment.dto.response.AssignmentRequestResponse> submit = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return requests.submit(id, repartidorId, key);
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            start.countDown();
            var result = first.get(15, TimeUnit.SECONDS);
            assertEquals(result, second.get(15, TimeUnit.SECONDS));
            assertEquals(result, requests.get(id, result.operationId()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE operation_id = ?",
                    Integer.class, result.operationId().toString()));
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.logitrack.shipment.exception.TransicionEstadoInvalidaException.class,
                    () -> requests.submit(id, UUID.randomUUID(), key));
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.logitrack.shipment.exception.TransicionEstadoInvalidaException.class,
                    () -> requests.submit(id, repartidorId, UUID.randomUUID()));
            assertNull(envioRepository.findById(id).orElseThrow().getRepartidorId());
        }
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Envio envio;
    private UUID repartidorId;

    @BeforeEach
    void setUp() {
        Direccion origen = Direccion.builder()
                .calle("Belgrano")
                .numero("123")
                .ciudad("San Salvador de Jujuy")
                .codigoPostal("4600")
                .build();

        Direccion destino = Direccion.builder()
                .calle("Alvear")
                .numero("456")
                .ciudad("San Salvador de Jujuy")
                .codigoPostal("4600")
                .build();

        envio = new Envio(
                UUID.randomUUID(),
                origen,
                destino,
                2.5,
                20.0,
                15.0,
                30.0
        );

        clienteRepository.asegurarExistencia(envio.getClienteId());
        envio = envioRepository.saveAndFlush(envio);
        repartidorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Asigna repartidor cuando el envío está pendiente y no tiene repartidor")
    void asignarRepartidorSiDisponible_debeActualizarUnaFila() {
        int filasActualizadas =
                envioRepository.asignarRepartidorSiDisponible(
                        envio.getId(),
                        repartidorId,
                        EstadoEnvio.PENDIENTE
                );

        assertEquals(1, filasActualizadas);

        Envio envioActualizado = envioRepository.findById(envio.getId())
                .orElseThrow();

        assertEquals(repartidorId, envioActualizado.getRepartidorId());
        assertEquals(EstadoEnvio.PENDIENTE, envioActualizado.getEstado());
    }

    @Test
    @DisplayName("Impide reasignar un envío que ya tiene repartidor")
    void asignarRepartidorSiDisponible_noDebePermitirUnaSegundaAsignacion() {
        UUID primerRepartidorId = UUID.randomUUID();
        UUID segundoRepartidorId = UUID.randomUUID();

        int primeraActualizacion =
                envioRepository.asignarRepartidorSiDisponible(
                        envio.getId(),
                        primerRepartidorId,
                        EstadoEnvio.PENDIENTE
                );

        int segundaActualizacion =
                envioRepository.asignarRepartidorSiDisponible(
                        envio.getId(),
                        segundoRepartidorId,
                        EstadoEnvio.PENDIENTE
                );

        assertEquals(1, primeraActualizacion);
        assertEquals(0, segundaActualizacion);

        Envio envioActualizado = envioRepository.findById(envio.getId())
                .orElseThrow();

        assertEquals(primerRepartidorId, envioActualizado.getRepartidorId());
    }

    @Test
    @DisplayName("No asigna repartidor si el estado no coincide")
    void asignarRepartidorSiDisponible_noDebeActualizarEstadoDiferente() {
        int filasActualizadas =
                envioRepository.asignarRepartidorSiDisponible(
                        envio.getId(),
                        repartidorId,
                        EstadoEnvio.EN_RUTA
                );

        assertEquals(0, filasActualizadas);

        Envio envioActualizado = envioRepository.findById(envio.getId())
                .orElseThrow();

        assertEquals(EstadoEnvio.PENDIENTE, envioActualizado.getEstado());
        assertNull(envioActualizado.getRepartidorId());
    }

    @Test
    @DisplayName("No asigna repartidor a un envío inexistente")
    void asignarRepartidorSiDisponible_noDebeActualizarIdInexistente() {
        int filasActualizadas =
                envioRepository.asignarRepartidorSiDisponible(
                        UUID.randomUUID(),
                        repartidorId,
                        EstadoEnvio.PENDIENTE
                );

        assertEquals(0, filasActualizadas);
    }

    @Test
    @DisplayName("Persiste el envío con identificador y código de seguimiento")
    void guardarEnvio_debeGenerarIdentificadores() {
        assertNotNull(envio.getId());
        assertNotNull(envio.getCodigoSeguimiento());
    }
    /** Comprueba la auditoría explícita de una escritura que omite callbacks JPA. */
    @Test
    void asignacionActualizaFechaYVersionSinCambiarCreacion() {
        entityManager.clear();
        Envio persistido = envioRepository.findById(envio.getId()).orElseThrow();
        Instant creacion = persistido.getCreatedDate();
        long version = persistido.getVersion();
        // DATETIME(6) conserva microsegundos; un segundo exacto evita que
        // el driver o MySQL redondeen el instante esperado al persistirlo.
        Instant modificacion = creacion.plusSeconds(10)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        assertEquals(1, envioRepository.asignarRepartidorSiDisponible(
                envio.getId(), repartidorId, EstadoEnvio.PENDIENTE, modificacion));

        Envio actualizado = envioRepository.findById(envio.getId()).orElseThrow();
        assertEquals(creacion, actualizado.getCreatedDate());
        assertEquals(modificacion, actualizado.getLastModifiedDate());
        assertEquals(version + 1, actualizado.getVersion());
    }

    /**
     * Ejecuta dos escrituras simultáneas sobre MySQL con transacciones separadas.
     *
     * <p>Se desactiva la transacción del test para que el alta inicial sea visible
     * desde ambos hilos. Cada llamada al repositorio confirma su propia escritura.</p>
     *
     * @throws Exception si un hilo falla o supera el tiempo de espera
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void soloUnaAsignacionConcurrentePersiste() throws Exception {
        UUID id = envio.getId();
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        long versionInicial = envio.getVersion();
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> {
                preparados.countDown();
                if (!inicio.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se habilitó la asignación A");
                }
                return envioRepository.asignarRepartidorSiDisponible(
                        id, primero, EstadoEnvio.PENDIENTE);
            });
            var b = executor.submit(() -> {
                preparados.countDown();
                if (!inicio.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se habilitó la asignación B");
                }
                return envioRepository.asignarRepartidorSiDisponible(
                        id, segundo, EstadoEnvio.PENDIENTE);
            });
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            int resultadoA = a.get(20, TimeUnit.SECONDS);
            int resultadoB = b.get(20, TimeUnit.SECONDS);
            assertEquals(1, resultadoA + resultadoB);

            Envio actualizado = envioRepository.findById(id).orElseThrow();
            assertEquals(resultadoA == 1 ? primero : segundo, actualizado.getRepartidorId());
            assertEquals(EstadoEnvio.PENDIENTE, actualizado.getEstado());
            assertEquals(versionInicial + 1, actualizado.getVersion());
        } finally {
            inicio.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            envioRepository.deleteById(id);
        }
    }

}
