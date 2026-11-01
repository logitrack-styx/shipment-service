package com.logitrack.shipment.client;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.logitrack.shipment.exception.CourierIntegrationException;

import lombok.RequiredArgsConstructor;

/**
 * Implementación de CourierClient delegando en el cliente declarativo OpenFeign.
 *
 * <p>Descubre courier-service dinámicamente vía Eureka, aplica tolerancia a fallos
 * y valida el contrato estructural de la respuesta recibida.</p>
 */
@Component
@RequiredArgsConstructor
public class CourierClientImpl implements CourierClient {

    private static final Set<String> ESTADOS_VALIDOS = Set.of("ACTIVO", "INACTIVO");

    private final CourierFeignClient courierFeignClient;

    /**
     * Consulta la disponibilidad del repartidor en courier-service a través de OpenFeign.
     *
     * @param repartidorId UUID del repartidor
     * @param authorizationHeader cabecera Bearer enviada al microservicio
     * @return {@code true} si está ACTIVO y disponible; {@code false} si está INACTIVO
     */
    @Override
    public boolean estaDisponible(UUID repartidorId, String authorizationHeader) {
        CourierDisponibilidadResponse response =
                courierFeignClient.consultarDisponibilidad(repartidorId, authorizationHeader);

        if (response == null
                || response.id() == null
                || response.disponible() == null
                || response.estado() == null
                || response.estado().isBlank()
                || !ESTADOS_VALIDOS.contains(response.estado())
                || !repartidorId.equals(response.id())
                || response.disponible().booleanValue() != "ACTIVO".equals(response.estado())) {

            throw new CourierIntegrationException(
                    HttpStatus.BAD_GATEWAY,
                    "courier-service devolvió una respuesta inválida");
        }

        return "ACTIVO".equals(response.estado());
    }
}
