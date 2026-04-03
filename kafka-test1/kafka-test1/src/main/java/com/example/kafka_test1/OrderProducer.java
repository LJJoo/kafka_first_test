package com.example.kafka_test1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OrderProducer {
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);
    private static final String TOPIC = "order-events";

    private volatile boolean paused = false;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RedisMessageBuffer redisBuffer;

    @Autowired
    private OrderRepository orderRepo;

    public void pause() {
        paused = true;
        logger.info("producer paused");
    }

    public void resume() {
        paused = false;
        logger.info("producer resumed, draining redis buffer size {}", redisBuffer.size());
        drainRedisBuffer();
    }

    public boolean isPaused() {
        return paused;
    }

    public void send(OrderEvent event) {
        if (paused) {
            redisBuffer.push(event.toString());
            logger.info("producer paused, buffered to redis orderId {}", event.getOrderId());
            return;
        }
        sendToKafka(event.toString(), event.getOrderId());
    }

    private void drainRedisBuffer() {
        String raw;
        int count = 0;
        while ((raw = redisBuffer.pop()) != null) {
            try {
                OrderEvent event = objectMapper.readValue(raw, OrderEvent.class);
                sendToKafka(raw, event.getOrderId());
                count++;
            } catch (Exception e) {
                logger.warn("drain failed for message {}, putting back", raw, e);
                redisBuffer.push(raw);
                break;
            }
        }
        logger.info("redis drain complete, {} messages sent to kafka", count);
    }

    private void sendToKafka(String json, long orderId) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(orderId), json);
            orderRepo.findById(orderId).ifPresent(o -> {
                o.setStatus("KAFKA_SENT");
                orderRepo.save(o);
            });
            logger.info("sent to kafka orderId {}", orderId);
        } catch (Exception e) {
            logger.warn("kafka send failed orderId {}", orderId, e);
        }
    }
}
