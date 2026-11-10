package com.logitrack.shipment.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the persisted state of an asynchronous assignment request.
 * @author dominox45
 */
public record AssignmentRequestResponse(
        UUID operationId, UUID envioId, UUID repartidorId, Status status, Instant createdAt, String reason) {

    /** Lifecycle states returned by the Sprint 4 API. */
    public enum Status { PENDING, CONFIRMED, REJECTED, FAILED }
}
