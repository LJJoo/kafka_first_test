package com.example.kafka_test1;

import lombok.Getter;
import lombok.Setter;

/**
 * Bean to hold order event message
 **/
@Getter
@Setter
public class OrderEvent {

    private long orderId;
    private String product;
    private int quantity;
    private int price;

    public OrderEvent() {}

    public OrderEvent(long orderId, String product, int quantity, int price) {
        this.orderId = orderId;
        this.product = product;
        this.quantity = quantity;
        this.price = price;
    }

    @Override
    public String toString() {
        return String.format(
                "{\"orderId\":%d,\"product\":\"%s\",\"quantity\":%d,\"price\":%d}",
                this.orderId, this.product, this.quantity, this.price);
    }
}
