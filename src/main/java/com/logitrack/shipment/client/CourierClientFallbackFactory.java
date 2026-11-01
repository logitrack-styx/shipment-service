package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierIntegrationException;
import com.logitrack.shipment.exception.RecursoNoEncontradoException;
import com.logitrack.shipment.exception.TransicionEstadoInvalidaException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fábrica de fallback para manejar la indisponibilidad de courier-service
 * sin comprometer la semántica de errores de negocio (como 404).
 */
@Component
public class CourierClientFallbackFactory implements FallbackFactory<CourierFeignClient> {

    @Override
    public CourierFeignClient create(Throwable cause) {
        return new CourierFeignClient() {
            @Override
            public CourierDisponibilidadResponse consultarDisponibilidad(
                    UUID repartidorId,
                    String authorizationHeader) {

                // Si la causa original es una excepción decodificada por CustomErrorDecoder, repropaga
                if (cause instanceof RecursoNoEncontradoException rne) {
                    throw rne;
                }
                if (cause instanceof TransicionEstadoInvalidaException tei) {
                    throw tei;
                }
                if (cause instanceof CourierIntegrationException cie) {
                    throw cie;
                }

                // Error de red, timeout, connection refused, circuito abierto o fallo del transporte
                throw new CourierIntegrationException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "courier-service no está disponible");
            }
        };
    }
}
