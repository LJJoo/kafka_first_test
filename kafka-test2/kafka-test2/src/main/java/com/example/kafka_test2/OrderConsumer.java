package com.example.kafka_test2;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OrderConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private OrderRepository orderRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "order-events", groupId = "order-group")
    public void consume(String message, @Header(KafkaHeaders.OFFSET) long offset) {
        logger.info("received message offset {} body {}", offset, message);

        try {
            OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);

            Order order = orderRepo.findById(orderMessage.getOrderId())
                    .orElseThrow(() -> new RuntimeException("order not found id " + orderMessage.getOrderId()));

            order.setStatus("PROCESSED");
            order.setKafkaOffset(offset);
            order.setProcessedAt(LocalDateTime.now());
            orderRepo.save(order);

            logger.info("order processed orderId {}", order.getId());
        } catch (Exception e) {
            logger.warn("order processing failed offset {}", offset, e);
            handleFailure(message, offset, e.getMessage());
        }
    }

    private void handleFailure(String message, long offset, String reason) {
        try {
            OrderMessage orderMessage = objectMapper.readValue(message, OrderMessage.class);

            orderRepo.findById(orderMessage.getOrderId()).ifPresent(order -> {
                order.setStatus("FAILED");
                order.setKafkaOffset(offset);
                order.setFailedReason(reason);
                order.setProcessedAt(LocalDateTime.now());
                orderRepo.save(order);
                logger.info("order marked as failed orderId {}", order.getId());
            });
        } catch (Exception e) {
            logger.warn("failed to update order status offset {}", offset, e);
        }
    }
}
