package com.example.kafka_test1;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(origins = "*")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrderProducer producer;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private RedisMessageBuffer redisBuffer;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/api/orders")
    public List<Order> orders() {
        logger.info("get all orders");
        return orderRepo.findAll();
    }

    @GetMapping("/api/order/{id}")
    public Order order(@PathVariable long id) {
        logger.info("get order {}", id);
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    @PostMapping("/api/producer/pause")
    public Map<String, Object> pauseProducer() {
        producer.pause();
        logger.info("producer pause requested");
        return Map.of("paused", true);
    }

    @PostMapping("/api/producer/resume")
    public Map<String, Object> resumeProducer() {
        producer.resume();
        logger.info("producer resume requested");
        return Map.of("paused", false);
    }

    @GetMapping("/api/producer/status")
    public Map<String, Object> producerStatus() {
        return Map.of("paused", producer.isPaused(), "bufferSize", redisBuffer.size());
    }

    @GetMapping("/api/producer/buffer")
    public Map<String, Object> getBuffer() {
        List<String> rawList = redisBuffer.getAll();
        List<Map<String, Object>> messages = rawList.stream().map(raw -> {
            try {
                return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return Map.<String, Object>of("raw", raw);
            }
        }).collect(Collectors.toList());
        return Map.of("count", redisBuffer.size(), "messages", messages);
    }

    @DeleteMapping("/api/producer/buffer")
    public Map<String, Object> clearBuffer() {
        long before = redisBuffer.size();
        redisBuffer.clear();
        logger.info("redis buffer cleared, {} messages removed", before);
        return Map.of("cleared", before);
    }

    @PostMapping(path = "/api/order", consumes = "application/json", produces = "application/json")
    public Order createOrder(@RequestBody Order order) {
        logger.info("create order {}", order);

        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        orderRepo.save(order);

        OrderEvent event = new OrderEvent(
                order.getId(),
                order.getProduct(),
                order.getQuantity(),
                order.getPrice()
        );
        producer.send(event);
        logger.info("order processed orderId {}", order.getId());

        return orderRepo.findById(order.getId()).orElse(order);
    }
}
