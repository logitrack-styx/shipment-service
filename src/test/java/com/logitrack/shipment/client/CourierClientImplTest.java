package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierIntegrationException;
import com.logitrack.shipment.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de CourierClientImpl adaptado a OpenFeign.
 */
@ExtendWith(MockitoExtension.class)
class CourierClientImplTest {

    @Mock
    private CourierFeignClient courierFeignClient;

    private CourierClientImpl courierClient;
    private UUID repartidorId;
    private final String authHeader = "Bearer token-prueba";

    @BeforeEach
    void setUp() {
        courierClient = new CourierClientImpl(courierFeignClient);
        repartidorId = UUID.randomUUID();
    }

    @Test
    void debeDevolverTrueCuandoElRepartidorEstaActivo() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenReturn(new CourierDisponibilidadResponse(repartidorId, true, "ACTIVO"));

        boolean disponible = courierClient.estaDisponible(repartidorId, authHeader);

        assertTrue(disponible);
        verify(courierFeignClient).consultarDisponibilidad(repartidorId, authHeader);
    }

    @Test
    void debeDevolverFalseCuandoElRepartidorEstaInactivo() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenReturn(new CourierDisponibilidadResponse(repartidorId, false, "INACTIVO"));

        boolean disponible = courierClient.estaDisponible(repartidorId, authHeader);

        assertFalse(disponible);
    }

    @Test
    void debeRechazarDisponibilidadContradictoria() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenReturn(new CourierDisponibilidadResponse(repartidorId, true, "INACTIVO"));

        CourierIntegrationException ex = assertThrows(
                CourierIntegrationException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void debeRechazarEstadoDesconocido() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenReturn(new CourierDisponibilidadResponse(repartidorId, false, "SUSPENDIDO"));

        CourierIntegrationException ex = assertThrows(
                CourierIntegrationException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void debeRechazarIdentificadorDiferenteAlSolicitado() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenReturn(new CourierDisponibilidadResponse(UUID.randomUUID(), true, "ACTIVO"));

        CourierIntegrationException ex = assertThrows(
                CourierIntegrationException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void debePropagarRecursoNoEncontradoCuandoCourierDevuelve404() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenThrow(new RecursoNoEncontradoException("No existe el repartidor especificado en courier-service"));

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));
    }

    @Test
    void debePropagarBadGatewayCuandoFallaAutenticacionInterServicio() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenThrow(new CourierIntegrationException(HttpStatus.BAD_GATEWAY, "Falló la autenticación"));

        CourierIntegrationException ex = assertThrows(
                CourierIntegrationException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void debePropagarServiceUnavailableCuandoCourierNoEstaDisponible() {
        when(courierFeignClient.consultarDisponibilidad(eq(repartidorId), eq(authHeader)))
                .thenThrow(new CourierIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "courier-service no está disponible"));

        CourierIntegrationException ex = assertThrows(
                CourierIntegrationException.class,
                () -> courierClient.estaDisponible(repartidorId, authHeader));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }
}
