package com.liamread.orders.stock;

import java.math.BigDecimal;

/**
 * What the ordering side is allowed to know about a SKU at pricing time.
 *
 * <p>Deliberately not the entity: quantities are stock's business, and handing {@code StockItem}
 * to {@code OrderService} would let it read — and eventually write — numbers it does not own.
 */
public record PricedSku(String sku, String description, BigDecimal unitPrice) {

    static PricedSku from(StockItem item) {
        return new PricedSku(item.getSku(), item.getDescription(), item.getUnitPrice());
    }
}
