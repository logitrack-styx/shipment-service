
package com.logitrack.shipment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.logitrack.shipment.dto.request.CambioEstadoRequest;
import com.logitrack.shipment.dto.request.EnvioRequest;
import com.logitrack.shipment.dto.response.EnvioResponse;
import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.service.interfaces.EnvioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/envios")
@RequiredArgsConstructor
@Tag(name = "Envíos", description = "Registro, consulta y ciclo de vida de envíos")
public class EnvioController {

    private final EnvioService envioService;

    @PostMapping
    @Operation(summary = "Registrar un envío")
    @ApiResponse(responseCode = "201", description = "Envío creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    public ResponseEntity<EnvioResponse> registrar(
            @Valid @RequestBody EnvioRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(envioService.registrar(request, subject(jwt), role(jwt)));
    }

    @GetMapping
    @Operation(summary = "Listar envíos, con filtro opcional por estado")
    @ApiResponse(responseCode = "400", description = "Estado inválido")
    public ResponseEntity<List<EnvioResponse>> listar(
            @RequestParam(required = false) EstadoEnvio estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        var result = envioService.listar(estado, jwt, page, size);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(result.getTotalElements()))
                .header("X-Total-Pages", Integer.toString(result.getTotalPages())).body(result.getContent());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un envío por id")
    @ApiResponse(responseCode = "404", description = "Envío inexistente")
    public EnvioResponse obtener(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        return envioService.obtenerPorId(id, subject(jwt), role(jwt));
    }

    @GetMapping("/seguimiento/{codigoSeguimiento}")
    @Operation(summary = "Consultar un envío por código de seguimiento")
    @ApiResponse(responseCode = "404", description = "Envío inexistente")
    public EnvioResponse obtenerPorSeguimiento(
            @PathVariable UUID codigoSeguimiento,
            @AuthenticationPrincipal Jwt jwt) {

        return envioService.obtenerPorCodigoSeguimiento(
                codigoSeguimiento,
                subject(jwt),
                role(jwt));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado de un envío")
    @ApiResponse(responseCode = "404", description = "Envío inexistente")
    @ApiResponse(responseCode = "409", description = "Transición no permitida")
    public EnvioResponse cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambioEstadoRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return envioService.cambiarEstado(
                id,
                request.estado(),
                subject(jwt),
                role(jwt));
    }

    @PatchMapping("/{id}/cancelacion")
    @Operation(summary = "Cancelar un envío PENDIENTE")
    @ApiResponse(responseCode = "204", description = "Envío cancelado")
    @ApiResponse(responseCode = "404", description = "Envío inexistente")
    @ApiResponse(responseCode = "409", description = "El envío no está PENDIENTE")
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        envioService.cancelarEnvio(id, subject(jwt), role(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El token no contiene un usuario autenticado");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El claim sub no contiene un UUID válido");
        }
    }

    private String role(Jwt jwt) {
        if (jwt == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Token no disponible");
        }

        Object roles = jwt.getClaims().get("roles");

        if (roles instanceof List<?> list
                && list.size() == 1
                && list.get(0) instanceof String role
                && !role.isBlank()) {
            return role;
        }

        throw new org.springframework.security.access.AccessDeniedException(
                "El claim roles no cumple el contrato");
    }
}
