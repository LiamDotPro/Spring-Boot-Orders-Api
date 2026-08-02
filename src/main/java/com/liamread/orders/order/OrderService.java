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
import com.liamread.orders.stock.PricedSku;
import com.liamread.orders.stock.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
public class OrderService {

    private final StockService stockService;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(
            StockService stockService,
            OrderRepository orderRepository,
            OrderEventPublisher orderEventPublisher
    ) {
        this.stockService = stockService;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    /**
     * Placing an order prices it and nothing more — no stock moves. "We have taken your order" and
     * "we have set your goods aside" are different promises, and {@link #acceptOrder} is where the
     * second one is made.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest requestInfo) {
        // Customer id should come from Spring Security decoding a jwt << taken from request for the moment.
        OrderEntity entity = new OrderEntity(requestInfo.customerId(), requestInfo.currency());

        for (OrderItem line : requestInfo.items()) {
            // Snapshot of today's price onto the line. Never looked up again — an order line
            // records what was agreed, and that does not change when marketing reprices.
            PricedSku priced = stockService.lookup(line.sku());
            entity.addLine(priced.sku(), priced.description(), line.quantity(), priced.unitPrice());
        }

        OrderEntity saved = orderRepository.save(entity);

        orderEventPublisher.publishOrderPlaced(OrderPlacedEvent.from(saved));

        return OrderResponse.from(saved);
    }

    /**
     * Reserve stock for every line, or none of them.
     *
     * <p>Two ordering details in here are load-bearing.
     *
     * <p><strong>The status is set before the allocations run.</strong> The stock updates are bulk
     * JPQL with {@code clearAutomatically}, which detaches this entity — any pending change not yet
     * flushed would be silently lost. Setting it first means {@code flushAutomatically} writes it
     * on the way into the first allocation. If a later line is short, the exception rolls the whole
     * transaction back, status included.
     *
     * <p><strong>SKUs are allocated in a deterministic order.</strong> Order A locking SKU-1 then
     * SKU-2 while order B locks SKU-2 then SKU-1 is a textbook deadlock, and Postgres resolves it
     * by killing one of them.
     */
    @Transactional
    public OrderResponse acceptOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // PENDING is accepted alongside PAID only until the payments listener exists — once
        // something moves orders to PAID on its own, tighten this to PAID and nothing else.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new InvalidStatusTransitionException(order.getStatus(), OrderStatus.ALLOCATED);
        }

        List<OrderItem> toAllocate = linesOf(order);

        order.setStatus(OrderStatus.ALLOCATED);
        toAllocate.forEach(line -> stockService.allocate(line.sku(), line.quantity()));

        log.info("Order {} ALLOCATED ({} lines)", orderId, toAllocate.size());
        return OrderResponse.from(reload(orderId));
    }

    /** The goods leave. Only legal from {@code ALLOCATED} — you cannot ship what was never reserved. */
    @Transactional
    public OrderResponse finalizeOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.ALLOCATED) {
            throw new InvalidStatusTransitionException(order.getStatus(), OrderStatus.SHIPPED);
        }

        List<OrderItem> toConsume = linesOf(order);

        order.setStatus(OrderStatus.SHIPPED);
        toConsume.forEach(line -> stockService.consume(line.sku(), line.quantity()));

        log.info("Order {} SHIPPED ({} lines)", orderId, toConsume.size());
        return OrderResponse.from(reload(orderId));
    }

    /**
     * Copies the lines out to plain values before any stock update runs, because those updates
     * detach the entities these came from.
     */
    private List<OrderItem> linesOf(OrderEntity order) {
        return order.getLines().stream()
                .map(line -> new OrderItem(line.getSku(), line.getQuantity()))
                .sorted(Comparator.comparing(OrderItem::sku))
                .toList();
    }

    private OrderEntity reload(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
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

    /**
     * Cancelling an {@code ALLOCATED} order has to give the stock back — otherwise every cancelled
     * order permanently reduces what the warehouse can sell. Cancelling a merely {@code PENDING}
     * one moves no quantities, because none were ever reserved.
     */
    @Transactional
    public CancelledOrderResponse cancelOrder(UUID orderId, String customerId) {
        OrderEntity foundOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderStatus orderStatus = foundOrder.getStatus();

        // Ensure the customerId Provided also matches the orders orginal customerId
        if (!Objects.equals(customerId, foundOrder.getCustomerId())) {
            throw new OrderAccessDeniedException(orderId, customerId);
        }

        // Terminal states cannot move again, and SHIPPED is past the point of no return: the goods
        // have left, so returning them is a refund, not a cancellation.
        if (orderStatus.isTerminal() || orderStatus == OrderStatus.SHIPPED) {
            throw new InvalidStatusTransitionException(orderStatus, OrderStatus.CANCELLED);
        }

        List<OrderItem> toRelease = orderStatus == OrderStatus.ALLOCATED ? linesOf(foundOrder) : List.of();

        // Set the status first, for the same reason as acceptOrder — the release below detaches
        // this entity. No save(): dirty checking flushes it.
        foundOrder.setStatus(OrderStatus.CANCELLED);
        toRelease.forEach(line -> stockService.release(line.sku(), line.quantity()));

        log.info("Order {} CANCELLED from {} ({} lines released)", orderId, orderStatus, toRelease.size());
        return new CancelledOrderResponse(orderId, OrderStatus.CANCELLED, Instant.now());
    }
}
