package com.logitrack.shipment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documenta la autenticación Bearer utilizada por los endpoints de envíos.
 *
 * <p>Permite enviar desde Swagger UI un JWT emitido por security-service.
 * La documentación no reemplaza los filtros de Spring Security ni las
 * comprobaciones de pertenencia realizadas en los servicios.</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "LogiTrack Shipment API", version = "Sprint 3"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
