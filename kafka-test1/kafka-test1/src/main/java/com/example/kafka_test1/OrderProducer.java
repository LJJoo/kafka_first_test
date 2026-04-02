package com.example.kafka_test1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderProducer {
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    private static final String TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void send(OrderEvent event) {
        logger.info("sending order event {}", event);

        try {
            kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), event.toString());
            logger.info("order event sent orderId {}", event.getOrderId());
        } catch (Exception e) {
            logger.warn("kafka send failed orderId {}", event.getOrderId(), e);
        }
    }
}
