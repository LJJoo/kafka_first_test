package com.example.kafka_test2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/*
 * Bean for Order
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "product")
    private String product;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "price")
    private int price;

    @Column(name = "status")
    private String status;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "failed_reason")
    private String failedReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Override
    public String toString() {
        return String.format("Order id: %d product: %s qty: %d status: %s",
                this.id, this.product, this.quantity, this.status);
    }
}
