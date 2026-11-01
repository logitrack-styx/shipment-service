package com.logitrack.shipment.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente declarativo Feign hacia courier-service descubierto dinámicamente vía Eureka.
 */
@FeignClient(
        name = "courier-service",
        fallbackFactory = CourierClientFallbackFactory.class,
        configuration = CourierFeignConfig.class
)
public interface CourierFeignClient {

    @GetMapping("/couriers/{repartidorId}/disponibilidad")
    CourierDisponibilidadResponse consultarDisponibilidad(
            @PathVariable("repartidorId") UUID repartidorId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    );
}
