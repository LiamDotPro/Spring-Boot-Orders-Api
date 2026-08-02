package com.liamread.orders.order;

import com.liamread.orders.order.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders-service")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/order")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse placedOrder = orderService.placeOrder(request);
        return ResponseEntity.accepted().body(placedOrder);
    }

    @GetMapping("/orders")
    public PagedModel<OrderListResponse> getOrders(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(orderService.getOrders(pageable));
    }

    @GetMapping("/order")
    public PagedModel<OrderResponse> getCustomerOrders(@RequestParam String customerId, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(orderService.getCustomerOrders(customerId, pageable));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<OrderResponse> getSingleOrder(@PathVariable UUID orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok().body(response);
    }

    /** Reserve stock for every line, or none — see {@code OrderService.acceptOrder}. */
    @PostMapping("/order/{orderId}/accept")
    public ResponseEntity<OrderResponse> acceptOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.acceptOrder(orderId));
    }

    /** Ship an allocated order: the reserved goods actually leave. */
    @PostMapping("/order/{orderId}/finalize")
    public ResponseEntity<OrderResponse> finalizeOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.finalizeOrder(orderId));
    }

    @PostMapping("/order/cancel")
    public ResponseEntity<CancelledOrderResponse> cancelOrder(@Valid @RequestBody CancelOrderRequest request) {
        CancelledOrderResponse response = orderService.cancelOrder(request.orderId(), request.customerId());
        return ResponseEntity.ok().body(response);
    }

}
