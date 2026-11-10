package com.logitrack.shipment.event;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Declares the durable Shipment eligibility queue and its dead-letter destination.
 * @author dominox45
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "shipment.assignment.messaging-enabled", havingValue = "true")
class AssignmentRabbitConfiguration {
    static final String EXCHANGE = "logitrack.domain.v1";
    static final String RESULTS = "shipment.assignment.eligibility.v1";
    static final String APPROVED = "courier.assignment.eligibility-approved.v1";
    static final String REJECTED = "courier.assignment.eligibility-rejected.v1";

    @Bean
    Declarables assignmentResultTopology() {
        var exchange = new TopicExchange(EXCHANGE);
        var deadLetters = QueueBuilder.durable(RESULTS + ".dlq").build();
        var results = QueueBuilder.durable(RESULTS).deadLetterExchange("")
                .deadLetterRoutingKey(deadLetters.getName()).build();
        return new Declarables(exchange, results, deadLetters,
                BindingBuilder.bind(results).to(exchange).with(APPROVED),
                BindingBuilder.bind(results).to(exchange).with(REJECTED));
    }
}
