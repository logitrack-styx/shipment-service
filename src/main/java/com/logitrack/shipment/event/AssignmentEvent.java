package com.logitrack.shipment.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned message exchanged between Shipment and Courier.
 * @author dominox45
 */
public record AssignmentEvent(UUID eventId, UUID operationId, Instant occurredAt,
                              int schemaVersion, UUID shipmentId, UUID courierId) {
    /** Rejects incomplete messages and unsupported schema versions. */
    public AssignmentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(courierId, "courierId");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Versión de evento no soportada");
        }
    }
}
