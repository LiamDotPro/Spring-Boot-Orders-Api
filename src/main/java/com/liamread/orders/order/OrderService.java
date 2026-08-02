package com.liamread.orders.order;

import com.liamread.orders.order.dto.CancelledOrderResponse;
import com.liamread.orders.order.dto.OrderListResponse;
import com.liamread.orders.order.dto.OrderResponse;
import com.liamread.orders.order.dto.PlaceOrderRequest;
import com.liamread.orders.order.event.OrderEventPublisher;
import com.liamread.orders.order.event.OrderPlacedEvent;
import com.liamread.orders.order.exception.InvalidStatusTransitionException;
import com.liamread.orders.order.exception.OrderAccessDeniedException;
import com.liamread.orders.order.exception.OrderNotFoundException;
import com.liamread.orders.order.exception.UnknownSkuException;
import com.liamread.orders.order.pricing.CatalogueItem;
import com.liamread.orders.order.pricing.PriceCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
public class OrderService {

    private final PriceCatalog priceCatalog;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(
            PriceCatalog priceCatalog,
            OrderRepository orderRepository,
            OrderEventPublisher orderEventPublisher
    ) {
        this.priceCatalog = priceCatalog;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest requestInfo) {
        // Customer id should come from Spring Security decoding a jwt << taken from request for the moment.
        OrderEntity entity = new OrderEntity(requestInfo.customerId(), requestInfo.currency());

        for (OrderItem line : requestInfo.items()) {
            CatalogueItem catalogueItem = priceCatalog.lookupItem(line.sku())
                    .orElseThrow(() -> new UnknownSkuException(line.sku()));
            entity.addLine(catalogueItem.sku(), catalogueItem.description(), line.quantity(), catalogueItem.unitPrice());
        }

        OrderEntity saved = orderRepository.save(entity);

        orderEventPublisher.publishOrderPlaced(OrderPlacedEvent.from(saved));

        return OrderResponse.from(saved);
    }

    /**
     * Record that the payments context accepted the charge.
     *
     * <p>Called by the listener that consumes {@code payments.payment-succeeded.v1}. Everything
     * about Kafka stays in that listener — this method is a plain state transition that a test can
     * call directly.
     *
     * <p>Silently does nothing unless the order is still {@code PENDING}. That guard is doing two
     * jobs: it makes a redelivered event harmless, and it stops a payment outcome that arrives
     * after a cancellation from resurrecting the order. Both are normal, not exceptional, so
     * neither throws.
     *
     * <p>No {@code save()} — the entity is managed inside this transaction, so Hibernate's dirty
     * checking writes the change at commit.
     */
    @Transactional
    public void markPaid(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Ignoring payment success for order {} — already {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        log.info("Order {} marked PAID", orderId);
    }

    /**
     * Record that the payments context declined the charge. See {@link #markPaid(UUID)} — the same
     * guard, and the same reasons for it.
     */
    @Transactional
    public void markPaymentFailed(UUID orderId, String reason) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Ignoring payment failure for order {} — already {}", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        log.info("Order {} marked FAILED — {}", orderId, reason);
    }

    @Transactional(readOnly = true)
    public Page<OrderListResponse> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderListResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId).map(OrderResponse::from).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional
    public CancelledOrderResponse cancelOrder(UUID orderId, String customerId) {
        OrderEntity foundOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderStatus orderStatus = foundOrder.getStatus();

        // Ensure the customerId Provided also matches the orders orginal customerId
        if (!Objects.equals(customerId, foundOrder.getCustomerId())) {
            throw new OrderAccessDeniedException(orderId, customerId);
        }

        // Check if the order is already refunded or delivered
        // Order is already cancelled
        if (orderStatus == OrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException(foundOrder.getStatus(), OrderStatus.CANCELLED);
        }

        // Order is already delivered
        if (orderStatus == OrderStatus.DELIVERED) {
            throw new InvalidStatusTransitionException(foundOrder.getStatus(), OrderStatus.DELIVERED);
        }

        // Order is already refunded
        if (orderStatus == OrderStatus.REFUNDED) {
            throw new InvalidStatusTransitionException(foundOrder.getStatus(), OrderStatus.REFUNDED);
        }

        // Order is already cancelled
        if (orderStatus == OrderStatus.SHIPPED) {
            throw new InvalidStatusTransitionException(foundOrder.getStatus(), OrderStatus.SHIPPED);
        }


        if (orderStatus == OrderStatus.FAILED) {
            throw new InvalidStatusTransitionException(foundOrder.getStatus(), OrderStatus.FAILED);
        }

        // Final step saving the order as cancelled
        foundOrder.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(foundOrder);

        return new CancelledOrderResponse(orderId, foundOrder.getStatus(), Instant.now());
    }
}
