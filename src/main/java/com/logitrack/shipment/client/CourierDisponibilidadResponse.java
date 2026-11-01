package com.logitrack.shipment.client;

import java.util.UUID;

/**
 * Representa la respuesta de disponibilidad emitida por courier-service.
 */
public record CourierDisponibilidadResponse(
        UUID id,
        Boolean disponible,
        String estado
) {}
