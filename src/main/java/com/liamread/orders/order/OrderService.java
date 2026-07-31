package com.liamread.orders.order;

import com.liamread.orders.order.dto.OrderListResponse;
import com.liamread.orders.order.dto.OrderResponse;
import com.liamread.orders.order.dto.PlaceOrderRequest;
import com.liamread.orders.order.exception.UnknownSkuException;
import com.liamread.orders.order.pricing.CatalogueItem;
import com.liamread.orders.order.pricing.PriceCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
public class OrderService {

    private final PriceCatalog priceCatalog;
    private final OrderRepository orderRepository;

    public OrderService(
            PriceCatalog priceCatalog,
            OrderRepository orderRepository
    ) {
        this.priceCatalog = priceCatalog;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest requestInfo) {
        // Customer id should come from Spring Security decoding a jwt << hardcoded for the moment
        OrderEntity entity = new OrderEntity("cust-01", requestInfo.currency());

        for (OrderItem line : requestInfo.items()) {
            CatalogueItem catalogueItem = priceCatalog.lookupItem(line.sku())
                    .orElseThrow(() -> new UnknownSkuException("Unknown sku: " + line.sku()));
            entity.addLine(catalogueItem.sku(), catalogueItem.description(), line.quantity(), catalogueItem.unitPrice());
        }

        return OrderResponse.from(orderRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<OrderListResponse> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderListResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }
}
