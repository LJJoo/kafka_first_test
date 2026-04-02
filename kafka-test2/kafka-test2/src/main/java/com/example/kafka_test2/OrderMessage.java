package com.example.kafka_test2;

import lombok.Getter;
import lombok.Setter;

/**
 * Bean to hold incoming order event message
 **/
@Getter
@Setter
public class OrderMessage {

    private long orderId;
    private String product;
    private int quantity;
    private int price;

    public OrderMessage() {}

    @Override
    public String toString() {
        return String.format("OrderMessage orderId: %d product: %s qty: %d price: %d",
                this.orderId, this.product, this.quantity, this.price);
    }
}
