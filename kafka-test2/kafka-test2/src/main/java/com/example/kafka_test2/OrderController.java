package com.example.kafka_test2;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "*")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private ConsumerControlService consumerControl;

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

    @GetMapping("/api/orders/status/{status}")
    public List<Order> ordersByStatus(@PathVariable String status) {
        logger.info("get orders by status {}", status);

        return orderRepo.findByStatus(status);
    }

    @PostMapping("/api/consumer/pause")
    public Map<String, Object> pauseConsumer() {
        consumerControl.pause();
        logger.info("consumer pause requested");
        return Map.of("paused", true, "lag", consumerControl.getLag());
    }

    @PostMapping("/api/consumer/resume")
    public Map<String, Object> resumeConsumer() {
        consumerControl.resume();
        logger.info("consumer resume requested");
        return Map.of("paused", false, "lag", consumerControl.getLag());
    }

    @GetMapping("/api/consumer/status")
    public Map<String, Object> consumerStatus() {
        return Map.of("paused", consumerControl.isPaused(), "lag", consumerControl.getLag());
    }
}
