package com.logitrack.shipment.event;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes leased outbox rows with mandatory routing and broker confirmation.
 * @author dominox45
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipment.assignment.messaging-enabled", havingValue = "true")
class AssignmentOutboxPublisher {
    private static final int MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    @Value("${shipment.assignment.result-timeout-seconds:300}")
    private long resultTimeoutSeconds;

    @Scheduled(fixedDelayString = "${shipment.assignment.poll-delay-ms:1000}")
    void publishDue() {
        jdbc.update("""
                UPDATE assignment_request SET status = 'FAILED', reason = ?
                WHERE status = 'PENDING' AND operation_id IN (
                    SELECT operation_id FROM outbox_events WHERE status = 'SENT' AND available_at <= ?
                )
                """, "No se recibió un resultado de Courier a tiempo", Timestamp.from(Instant.now()));
        var rows = jdbc.query("""
                SELECT e.event_id, e.operation_id, e.routing_key, e.occurred_at, e.attempts,
                       r.envio_id, r.repartidor_id
                FROM outbox_events e JOIN assignment_request r ON r.operation_id = e.operation_id
                WHERE e.status = 'PENDING' AND (e.available_at IS NULL OR e.available_at <= ?)
                ORDER BY e.occurred_at, e.event_id LIMIT 20
                """, (row, index) -> new Pending(new AssignmentEvent(
                        UUID.fromString(row.getString("event_id")), UUID.fromString(row.getString("operation_id")),
                        row.getTimestamp("occurred_at").toInstant(), 1,
                        UUID.fromString(row.getString("envio_id")), UUID.fromString(row.getString("repartidor_id"))),
                        row.getString("routing_key"), row.getInt("attempts")), Timestamp.from(Instant.now()));
        for (var row : rows) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            publish(row);
        }
    }

    private void publish(Pending row) {
        var event = row.event();
        String id = event.eventId().toString();
        String lease = UUID.randomUUID().toString();
        var now = Instant.now();
        int claimed = jdbc.update("""
                UPDATE outbox_events SET attempts = attempts + 1, lease_token = ?, available_at = ?
                WHERE event_id = ? AND status = 'PENDING' AND attempts = ?
                  AND (available_at IS NULL OR available_at <= ?)
                """, lease, Timestamp.from(now.plusSeconds(60)), id, row.attempts(), Timestamp.from(now));
        if (claimed == 0) {
            return;
        }
        if (row.attempts() >= MAX_ATTEMPTS) {
            fail(event, lease);
            return;
        }
        try {
            var correlation = new CorrelationData(lease);
            var message = MessageBuilder.withBody(mapper.writeValueAsBytes(event))
                    .setContentType("application/json")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(id)
                    .setCorrelationId(event.operationId().toString()).build();
            rabbit.send(AssignmentRabbitConfiguration.EXCHANGE, row.routingKey(), message, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new AmqpException("RabbitMQ no confirmó el enrutamiento");
            }
            jdbc.update("""
                    UPDATE outbox_events SET status = 'SENT', lease_token = NULL, available_at = ?
                    WHERE event_id = ? AND lease_token = ?
                    """, Timestamp.from(Instant.now().plusSeconds(resultTimeoutSeconds)), id, lease);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | JsonProcessingException | ExecutionException | TimeoutException failure) {
            int attempt = row.attempts() + 1;
            if (attempt >= MAX_ATTEMPTS) {
                fail(event, lease);
            } else {
                jdbc.update("""
                        UPDATE outbox_events SET lease_token = NULL, available_at = ?
                        WHERE event_id = ? AND lease_token = ?
                        """, Timestamp.from(Instant.now().plusSeconds(1L << attempt)), id, lease);
            }
            log.warn("Assignment event {} publication failed on attempt {}", id, attempt);
        }
    }

    private void fail(AssignmentEvent event, String lease) {
        transactions.executeWithoutResult(transaction -> {
            int updated = jdbc.update("""
                    UPDATE outbox_events SET status = 'FAILED', lease_token = NULL, available_at = NULL
                    WHERE event_id = ? AND lease_token = ?
                    """, event.eventId().toString(), lease);
            if (updated == 1) {
                jdbc.update("""
                        UPDATE assignment_request SET status = 'FAILED', reason = ?
                        WHERE operation_id = ? AND status = 'PENDING'
                        """, "No se pudo publicar la solicitud de asignación", event.operationId().toString());
            }
        });
    }

    private record Pending(AssignmentEvent event, String routingKey, int attempts) {}
}
