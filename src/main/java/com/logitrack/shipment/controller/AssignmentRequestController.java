package com.logitrack.shipment.controller;

import java.util.UUID;

import com.logitrack.shipment.dto.request.AsignacionRequest;
import com.logitrack.shipment.dto.response.AssignmentRequestResponse;
import com.logitrack.shipment.service.impl.AssignmentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the administrative assignment request API.
 * @author dominox45
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/envios/{id}/assignment-requests")
public class AssignmentRequestController {
    private final AssignmentRequestService assignments;

    /** Accepts an idempotent request and records it for later publication. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "shipmentAssignmentCreate", summary = "Solicitar una asignación asíncrona")
    public AssignmentRequestResponse submit(@PathVariable UUID id,
            @Parameter(required = true) @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AsignacionRequest request) {
        if (key == null) {
            throw new IllegalArgumentException("Idempotency-Key es obligatoria");
        }
        UUID parsed = UUID.fromString(key);
        if (!parsed.toString().equalsIgnoreCase(key)) {
            throw new IllegalArgumentException("Idempotency-Key debe ser un UUID canónico");
        }
        return assignments.submit(id, request.getRepartidorId(), parsed);
    }

    /** Returns the current persisted state for the shipment and operation. */
    @GetMapping("/{operationId}")
    @Operation(operationId = "shipmentAssignmentGet", summary = "Consultar el estado de una asignación asíncrona")
    public AssignmentRequestResponse get(@PathVariable UUID id, @PathVariable UUID operationId) {
        return assignments.get(id, operationId);
    }
}
