package com.logitrack.shipment.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import com.logitrack.shipment.dto.response.AssignmentRequestResponse;
import com.logitrack.shipment.dto.response.AssignmentRequestResponse.Status;
import com.logitrack.shipment.enums.EstadoEnvio;
import com.logitrack.shipment.exception.RecursoNoEncontradoException;
import com.logitrack.shipment.exception.TransicionEstadoInvalidaException;
import com.logitrack.shipment.repository.EnvioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Stores assignment requests and their publication events atomically.
 * @author dominox45
 */
@Service
@RequiredArgsConstructor
public class AssignmentRequestService {
    private static final RowMapper<AssignmentRequestResponse> RESPONSE = (row, index) ->
            new AssignmentRequestResponse(UUID.fromString(row.getString("operation_id")),
                    UUID.fromString(row.getString("envio_id")), UUID.fromString(row.getString("repartidor_id")),
                    Status.valueOf(row.getString("status")), row.getTimestamp("created_at").toInstant(),
                    row.getString("reason"));
    private final EnvioRepository envios;
    private final JdbcTemplate jdbc;

    /** Creates a request or returns the operation previously created with the same key and payload. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentRequestResponse submit(UUID envioId, UUID repartidorId, UUID key) {
        String hash = fingerprint(envioId, repartidorId);
        var envio = envios.findForAssignment(envioId).orElse(null);
        var existing = replay(key, hash);
        if (existing != null) {
            return existing;
        }
        if (envio == null) {
            throw new RecursoNoEncontradoException("No existe un envío con id " + envioId);
        }
        if (envio.getEstado() != EstadoEnvio.PENDIENTE || envio.getRepartidorId() != null
                || Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM assignment_request WHERE envio_id = ? AND status = 'PENDING')
                        """, Boolean.class, envioId.toString()))) {
            throw new TransicionEstadoInvalidaException("El envío no admite otra solicitud de asignación");
        }

        var operation = UUID.randomUUID();
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            jdbc.update("""
                    INSERT INTO assignment_request
                        (operation_id, idempotency_key, request_hash, envio_id, repartidor_id, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                    """, operation.toString(), key.toString(), hash, envioId.toString(), repartidorId.toString(),
                    Timestamp.from(now));
        } catch (DuplicateKeyException conflict) {
            var concurrent = replay(key, hash);
            if (concurrent != null) {
                return concurrent;
            }
            throw conflict;
        }
        jdbc.update("""
                INSERT INTO outbox_events (event_id, operation_id, routing_key, occurred_at)
                VALUES (?, ?, 'courier.assignment.requested.v1', ?)
                """, UUID.randomUUID().toString(), operation.toString(), Timestamp.from(now));
        return new AssignmentRequestResponse(operation, envioId, repartidorId, Status.PENDING, now, null);
    }

    /** Reads an operation only within its shipment. */
    @Transactional(readOnly = true)
    public AssignmentRequestResponse get(UUID envioId, UUID operationId) {
        return jdbc.query("SELECT * FROM assignment_request WHERE envio_id = ? AND operation_id = ?",
                RESPONSE, envioId.toString(), operationId.toString()).stream().findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud de asignación"));
    }

    private AssignmentRequestResponse replay(UUID key, String hash) {
        return jdbc.query("SELECT * FROM assignment_request WHERE idempotency_key = ? FOR UPDATE", (row, index) -> {
            if (!hash.equals(row.getString("request_hash"))) {
                throw new TransicionEstadoInvalidaException("Idempotency-Key ya se usó con otra solicitud");
            }
            return RESPONSE.mapRow(row, index);
        }, key.toString()).stream().findFirst().orElse(null);
    }

    private String fingerprint(UUID envioId, UUID repartidorId) {
        try {
            var payload = (envioId + ":" + repartidorId).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
