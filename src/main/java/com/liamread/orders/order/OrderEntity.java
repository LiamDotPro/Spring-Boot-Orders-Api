package com.liamread.orders.order;

import com.liamread.orders.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class OrderEntity extends BaseEntity {

    private String customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(precision = 19, scale = 4)
    private BigDecimal total;

    private String currency;
    private Instant placedAt;

    @BatchSize(size = 50)   // org.hibernate.annotations
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderLineEntity> lines = new ArrayList<>();


    protected OrderEntity() { }   // for Hibernate only

    /** No {@code total} parameter by design — it is derived from the lines, never supplied. */
    public OrderEntity(String customerId, String currency) {
        this.customerId = customerId;
        this.currency = currency;
        this.status = OrderStatus.PENDING;
        this.placedAt = Instant.now();
        this.total = BigDecimal.ZERO;
    }

    public void addLine(String sku, String description, int quantity, BigDecimal unitPrice) {
        lines.add(new OrderLineEntity(this, sku, description, quantity, unitPrice));
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.total = lines.stream()
                .map(OrderLineEntity::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<OrderLineEntity> getLines() {
        return Collections.unmodifiableList(lines);
    }
}