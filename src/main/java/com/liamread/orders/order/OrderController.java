package com.liamread.orders.order;

import com.liamread.orders.order.dto.OrderListResponse;
import com.liamread.orders.order.dto.OrderResponse;
import com.liamread.orders.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

}
