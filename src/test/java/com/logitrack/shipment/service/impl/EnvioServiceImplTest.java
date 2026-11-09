package com.logitrack.shipment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.logitrack.shipment.dto.request.DireccionDto;
import com.logitrack.shipment.dto.request.EnvioRequest;
import com.logitrack.shipment.dto.response.EnvioResponse;
import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.event.EnvioEstadoCambiadoEvent;
import com.logitrack.shipment.model.Envio;
import com.logitrack.shipment.repository.EnvioRepository;

import static org.mockito.Mockito.times;

import java.util.Optional;

import com.logitrack.shipment.exception.RecursoNoEncontradoException;

/**
 * Pruebas unitarias de la implementación del servicio de envíos.
 *
 * @author dominox45
 */
@ExtendWith(MockitoExtension.class)
class EnvioServiceImplTest {

    @Mock
    private EnvioRepository envioRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EnvioServiceImpl envioService;

    @Mock
    private com.logitrack.shipment.repository.ClienteRepository clienteRepository;
    @Mock
    private com.logitrack.shipment.client.ClienteIdentityVerifier clienteIdentityVerifier;

    private UUID clienteId;
    private EnvioRequest request;
    private Jwt jwtCliente;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();

        jwtCliente = crearJwt(clienteId, "CLIENTE");

        request = new EnvioRequest(
                new DireccionDto(
                        "Belgrano", "123",
                        "San Salvador de Jujuy", "4600"),
                new DireccionDto(
                        "Alvear", "456",
                        "San Salvador de Jujuy", "4600"),
                2.5,
                20.0,
                15.0,
                30.0);
    }

    @Test
    void filtraListadosPersonalesPorIdentidadYEstado() {
        var pagina = com.logitrack.shipment.config.BoundedPage.of(1, 10);
        var courierId = UUID.randomUUID();
        var courier = crearJwt(courierId, "REPARTIDOR");
        when(envioRepository.findByClienteId(clienteId, pagina))
                .thenReturn(org.springframework.data.domain.Page.empty(pagina));
        when(envioRepository.findByRepartidorIdAndEstado(courierId, EstadoEnvio.EN_RUTA, pagina))
                .thenReturn(org.springframework.data.domain.Page.empty(pagina));

        assertEquals(1, envioService.listarPropios(null, jwtCliente, 1, 10).getNumber());
        assertEquals(1, envioService.listarAsignados(EstadoEnvio.EN_RUTA, courier, 1, 10).getNumber());
        assertThrows(AccessDeniedException.class, () -> envioService.listarPropios(null, courier, 0, 20));
        assertThrows(AccessDeniedException.class, () -> envioService.listarAsignados(null, jwtCliente, 0, 20));
    }

    @Test
    void debeRegistrarEnvioPendienteYPublicarEventoInicial() {
        when(envioRepository.save(any(Envio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse respuesta = envioService.registrar(request, jwtCliente);

        assertNotNull(respuesta);
        assertEquals(clienteId, respuesta.clienteId());
        assertEquals(EstadoEnvio.PENDIENTE, respuesta.estado());
        assertEquals(2.5, respuesta.peso());
        assertNotNull(respuesta.codigoSeguimiento());

        ArgumentCaptor<Envio> envioCaptor =
                ArgumentCaptor.forClass(Envio.class);

        verify(envioRepository).save(envioCaptor.capture());

        Envio guardado = envioCaptor.getValue();

        assertEquals(clienteId, guardado.getClienteId());
        assertEquals(EstadoEnvio.PENDIENTE, guardado.getEstado());

        ArgumentCaptor<EnvioEstadoCambiadoEvent> eventoCaptor =
                ArgumentCaptor.forClass(EnvioEstadoCambiadoEvent.class);

        verify(eventPublisher).publishEvent(eventoCaptor.capture());

        EnvioEstadoCambiadoEvent evento = eventoCaptor.getValue();

        assertEquals(guardado, evento.envio());
        assertEquals(null, evento.estadoAnterior());
        assertEquals(EstadoEnvio.PENDIENTE, evento.estadoNuevo());
    }

    @Test
    void noDebePublicarEventoSiFallaLaPersistencia() {
        when(envioRepository.save(any(Envio.class)))
                .thenThrow(new RuntimeException("Error de persistencia"));

        assertThrows(
                RuntimeException.class,
                () -> envioService.registrar(request, jwtCliente));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void debeRechazarElRegistroSiElRolNoEstaAutorizado() {
        Jwt jwtRepartidor = crearJwt(UUID.randomUUID(), "REPARTIDOR");

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.registrar(request, jwtRepartidor));

        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void debeRechazarElTokenSinRol() {
        Jwt jwtSinRol = Jwt.withTokenValue("token-test")
                .header("alg", "RS256")
                .subject(clienteId.toString())
                .build();

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.registrar(request, jwtSinRol));

        verify(envioRepository, never()).save(any(Envio.class));
    }

    @Test
    void clienteDebePoderConsultarSuPropioEnvio() {
        Envio envio = nuevoEnvio(clienteId);
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(Optional.of(envio));

        EnvioResponse respuesta =
                envioService.obtenerPorId(envioId, jwtCliente);

        assertNotNull(respuesta);
        assertEquals(clienteId, respuesta.clienteId());

        verify(envioRepository).findById(envioId);
    }

    @Test
    void clienteNoDebeConsultarEnvioDeOtroCliente() {
        UUID otroClienteId = UUID.randomUUID();
        Envio envio = nuevoEnvio(otroClienteId);
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.obtenerPorId(envioId, jwtCliente));

        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repartidorDebePoderConsultarEnvioAsignado() {
        UUID repartidorId = UUID.randomUUID();
        Jwt jwtRepartidor = crearJwt(repartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorId);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(Optional.of(envio));

        EnvioResponse respuesta =
                envioService.obtenerPorId(envioId, jwtRepartidor);

        assertNotNull(respuesta);
        assertEquals(clienteId, respuesta.clienteId());

        verify(envioRepository).findById(envioId);
    }

    @Test
    void repartidorNoDebeConsultarEnvioAsignadoAOtroRepartidor() {
        UUID repartidorAsignadoId = UUID.randomUUID();
        UUID otroRepartidorId = UUID.randomUUID();

        Jwt jwtRepartidor = crearJwt(otroRepartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorAsignadoId);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.obtenerPorId(envioId, jwtRepartidor));

        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void clienteNoDebeListarTodosLosEnvios() {
        assertThrows(
                AccessDeniedException.class,
                () -> envioService.listar(null, jwtCliente));

        verify(envioRepository, never()).findAll();
        verify(envioRepository, never()).findByEstado(any(EstadoEnvio.class));
    }

    @Test
    void repartidorNoDebeListarTodosLosEnvios() {
        Jwt jwtRepartidor = crearJwt(UUID.randomUUID(), "REPARTIDOR");

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.listar(null, jwtRepartidor));

        verify(envioRepository, never()).findAll();
        verify(envioRepository, never()).findByEstado(any(EstadoEnvio.class));
    }
    @Test
    void clientePropietarioDebePoderCancelarEnvioPendiente() {
        Envio envio = nuevoEnvio(clienteId);
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));
        when(envioRepository.save(any(Envio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        envioService.cancelarEnvio(envioId, jwtCliente);

        assertEquals(EstadoEnvio.CANCELADO, envio.getEstado());
        verify(envioRepository).save(envio);

        ArgumentCaptor<EnvioEstadoCambiadoEvent> captor =
                ArgumentCaptor.forClass(EnvioEstadoCambiadoEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        assertEquals(EstadoEnvio.PENDIENTE, captor.getValue().estadoAnterior());
        assertEquals(EstadoEnvio.CANCELADO, captor.getValue().estadoNuevo());
    }

    @Test
    void clienteNoDebeCancelarEnvioDeOtroCliente() {
        Envio envio = nuevoEnvio(UUID.randomUUID());
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.cancelarEnvio(envioId, jwtCliente));

        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repartidorNoDebeCancelarEnvio() {
        Envio envio = nuevoEnvio(clienteId);
        UUID repartidorId = UUID.randomUUID();
        envio.asignarRepartidor(repartidorId);

        UUID envioId = UUID.randomUUID();
        Jwt jwtRepartidor = crearJwt(repartidorId, "REPARTIDOR");

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.cancelarEnvio(envioId, jwtRepartidor));

        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void administradorDebePoderCancelarEnvioPendiente() {
        Envio envio = nuevoEnvio(clienteId);
        UUID envioId = UUID.randomUUID();
        Jwt jwtAdmin = crearJwt(UUID.randomUUID(), "ADMIN");

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));
        when(envioRepository.save(any(Envio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        envioService.cancelarEnvio(envioId, jwtAdmin);

        assertEquals(EstadoEnvio.CANCELADO, envio.getEstado());
        verify(envioRepository).save(envio);
        verify(eventPublisher).publishEvent(any(EnvioEstadoCambiadoEvent.class));
    }

    @Test
    void noDebeCancelarEnvioQueYaEstaEnRuta() {
        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(UUID.randomUUID());
        envio.cambiarEstado(EstadoEnvio.EN_RUTA);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));

        assertThrows(
                com.logitrack.shipment.exception.TransicionEstadoInvalidaException.class,
                () -> envioService.cancelarEnvio(envioId, jwtCliente));

        assertEquals(EstadoEnvio.EN_RUTA, envio.getEstado());
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repartidorAsignadoDebePoderCambiarEnvioAEnRuta() {
        UUID repartidorId = UUID.randomUUID();
        Jwt jwtRepartidor = crearJwt(repartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorId);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));
        when(envioRepository.save(any(Envio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse respuesta = envioService.cambiarEstado(
                envioId, EstadoEnvio.EN_RUTA, jwtRepartidor);

        assertEquals(EstadoEnvio.EN_RUTA, respuesta.estado());
        assertEquals(EstadoEnvio.EN_RUTA, envio.getEstado());

        ArgumentCaptor<EnvioEstadoCambiadoEvent> captor =
                ArgumentCaptor.forClass(EnvioEstadoCambiadoEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        assertEquals(EstadoEnvio.PENDIENTE, captor.getValue().estadoAnterior());
        assertEquals(EstadoEnvio.EN_RUTA, captor.getValue().estadoNuevo());
    }

    @Test
    void repartidorAsignadoDebePoderEntregarEnvioEnRuta() {
        UUID repartidorId = UUID.randomUUID();
        Jwt jwtRepartidor = crearJwt(repartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorId);
        envio.cambiarEstado(EstadoEnvio.EN_RUTA);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));
        when(envioRepository.save(any(Envio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnvioResponse respuesta = envioService.cambiarEstado(
                envioId, EstadoEnvio.ENTREGADO, jwtRepartidor);

        assertEquals(EstadoEnvio.ENTREGADO, respuesta.estado());

        ArgumentCaptor<EnvioEstadoCambiadoEvent> captor =
                ArgumentCaptor.forClass(EnvioEstadoCambiadoEvent.class);

        verify(eventPublisher).publishEvent(captor.capture());

        assertEquals(EstadoEnvio.EN_RUTA, captor.getValue().estadoAnterior());
        assertEquals(EstadoEnvio.ENTREGADO, captor.getValue().estadoNuevo());
    }

    @Test
    void repartidorNoAsignadoNoDebeCambiarEstado() {
        UUID repartidorAsignadoId = UUID.randomUUID();
        UUID otroRepartidorId = UUID.randomUUID();

        Jwt jwtRepartidor = crearJwt(otroRepartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorAsignadoId);

        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.cambiarEstado(
                        envioId, EstadoEnvio.EN_RUTA, jwtRepartidor));

        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());

        verify(envioRepository).findById(envioId);
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void clienteNoDebeCambiarEstadoDelEnvio() {
        Envio envio = nuevoEnvio(clienteId);
        UUID envioId = UUID.randomUUID();

        when(envioRepository.findById(envioId))
                .thenReturn(java.util.Optional.of(envio));

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.cambiarEstado(
                        envioId, EstadoEnvio.CANCELADO, jwtCliente));

        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repartidorNoDebeSolicitarCancelacionComoCambioDeEstado() {
        UUID repartidorId = UUID.randomUUID();
        Jwt jwtRepartidor = crearJwt(repartidorId, "REPARTIDOR");

        Envio envio = nuevoEnvio(clienteId);
        envio.asignarRepartidor(repartidorId);

        UUID envioId = UUID.randomUUID();

        assertThrows(
                AccessDeniedException.class,
                () -> envioService.cambiarEstado(
                        envioId, EstadoEnvio.CANCELADO, jwtRepartidor));

        assertEquals(EstadoEnvio.PENDIENTE, envio.getEstado());

        verify(envioRepository, never()).findById(any(UUID.class));
        verify(envioRepository, never()).save(any(Envio.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private Envio nuevoEnvio(UUID propietarioId) {
        return new Envio(
                propietarioId,
                request.origen().aEntidad(),
                request.destino().aEntidad(),
                request.peso(),
                request.ancho(),
                request.alto(),
                request.largo());
    }


    private Jwt crearJwt(UUID subject, String rol) {
        return Jwt.withTokenValue("token-test")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("roles", List.of(rol))
                .build();
    }
}
