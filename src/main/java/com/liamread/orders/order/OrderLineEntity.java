package com.liamread.orders.order;

import com.liamread.orders.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_lines")
@Getter
public class OrderLineEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(nullable = false)
    private String sku;

    private String description;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot of the catalogue price at the time of ordering, not a live reference. */
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    protected OrderLineEntity() { }

    OrderLineEntity(OrderEntity order, String sku, String description, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.sku = sku;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}