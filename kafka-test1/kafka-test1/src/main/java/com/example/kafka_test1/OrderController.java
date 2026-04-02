package com.example.kafka_test1;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "*")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderProducer producer;

    @Autowired
    private OrderRepository orderRepo;

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
        logger.info("order published to kafka orderId {}", order.getId());

        return order;
    }
}
