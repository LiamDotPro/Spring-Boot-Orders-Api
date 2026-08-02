package com.liamread.orders.order.consumer;

import com.liamread.orders.order.OrderService;
import com.liamread.orders.order.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderPlacedListener {

    private final OrderService orderService;   // or OrderRepository — see below

    public OrderPlacedListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = "orders.order-placed.v1",
            groupId = "orders-fulfilment",
            concurrency = "3")
    public void onOrderPlaced(
            OrderPlacedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {

        log.info(event.orderId(), partition, offset);

        // log partition + offset
        // move the order PENDING -> PROCESSING
    }

    @KafkaListener(topics = "orders.order-placed.v1", groupId = "orders-audit")
    public void auditOrderPlaced(OrderPlacedEvent event) {
        // just log
    }
}