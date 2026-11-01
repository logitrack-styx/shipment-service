package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierIntegrationException;
import com.logitrack.shipment.exception.RecursoNoEncontradoException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

/**
 * Configuración de OpenFeign para la integración con courier-service.
 */
public class CourierFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }

    public static class CustomErrorDecoder implements ErrorDecoder {

        @Override
        public Exception decode(String methodKey, Response response) {
            int status = response.status();

            if (status == 404) {
                return new RecursoNoEncontradoException("No existe el repartidor especificado en courier-service");
            }
            if (status == 401 || status == 403) {
                return new CourierIntegrationException(
                        HttpStatus.BAD_GATEWAY,
                        "Falló la autenticación entre shipment-service y courier-service");
            }
            if (status >= 500) {
                return new CourierIntegrationException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "courier-service no está disponible");
            }
            return new CourierIntegrationException(
                    HttpStatus.BAD_GATEWAY,
                    "courier-service respondió con un estado HTTP inesperado: " + status);
        }
    }
}
