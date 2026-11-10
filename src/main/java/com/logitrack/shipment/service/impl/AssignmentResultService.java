package com.logitrack.shipment.service.impl;

import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.event.AssignmentEvent;
import com.logitrack.shipment.repository.EnvioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a Courier eligibility result and commits the shipment and operation together.
 * @author dominox45
 */
@Service
@RequiredArgsConstructor
public class AssignmentResultService {
    private final EnvioRepository envios;
    private final JdbcTemplate jdbc;

    /** Applies a result once, leaving terminal operations unchanged. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(AssignmentEvent event, boolean approved) {
        boolean shipmentExists = envios.findForAssignment(event.shipmentId()).isPresent();
        String operation = event.operationId().toString();
        var states = jdbc.query("""
                SELECT status FROM assignment_request
                WHERE operation_id = ? AND envio_id = ? AND repartidor_id = ? FOR UPDATE
                """, (row, index) -> row.getString("status"), operation,
                event.shipmentId().toString(), event.courierId().toString());
        if (states.isEmpty()) {
            throw new IllegalArgumentException("El resultado no corresponde a la solicitud");
        }
        if (!"PENDING".equals(states.getFirst())) {
            return;
        }
        try {
            jdbc.update("INSERT INTO processed_assignment_results (event_id, operation_id) VALUES (?, ?)",
                    event.eventId().toString(), operation);
        } catch (DuplicateKeyException duplicate) {
            return;
        }

        boolean assigned = approved && shipmentExists && envios.asignarRepartidorSiDisponible(
                event.shipmentId(), event.courierId(), EstadoEnvio.PENDIENTE) == 1;
        String reason = approved ? "El envío ya no admite asignación" : "El repartidor no está disponible";
        jdbc.update("UPDATE assignment_request SET status = ?, reason = ? WHERE operation_id = ?",
                assigned ? "CONFIRMED" : "REJECTED", assigned ? null : reason, operation);
    }
}
