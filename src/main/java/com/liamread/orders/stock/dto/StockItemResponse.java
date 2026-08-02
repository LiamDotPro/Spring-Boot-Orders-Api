package com.liamread.orders.stock.dto;

import com.liamread.orders.stock.StockItem;

import java.math.BigDecimal;

/**
 * {@code quantityAvailable} is present here and absent from the database. The API is the right
 * place to expose derived state; the schema is the wrong place to store it.
 */
public record StockItemResponse(
        String sku,
        String description,
        BigDecimal unitPrice,
        int quantityOnHand,
        int quantityAllocated,
        int quantityAvailable
) {

    public static StockItemResponse from(StockItem item) {
        return new StockItemResponse(
                item.getSku(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getQuantityOnHand(),
                item.getQuantityAllocated(),
                item.getQuantityAvailable()
        );
    }
}
