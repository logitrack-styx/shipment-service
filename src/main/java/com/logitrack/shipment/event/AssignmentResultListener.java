package com.logitrack.shipment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.service.impl.AssignmentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles RabbitMQ eligibility replies and acknowledges after the database commit.
 * @author dominox45
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipment.assignment.messaging-enabled", havingValue = "true")
class AssignmentResultListener {
    private final ObjectMapper mapper;
    private final AssignmentResultService results;

    @RabbitListener(queues = AssignmentRabbitConfiguration.RESULTS)
    void receive(Message message) {
        try {
            boolean approved = switch (message.getMessageProperties().getReceivedRoutingKey()) {
                case AssignmentRabbitConfiguration.APPROVED -> true;
                case AssignmentRabbitConfiguration.REJECTED -> false;
                default -> throw new IllegalArgumentException("Ruta de resultado inválida");
            };
            results.apply(mapper.readValue(message.getBody(), AssignmentEvent.class), approved);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new AmqpRejectAndDontRequeueException("Resultado de asignación inválido", invalid);
        }
    }
}
