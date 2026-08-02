package com.liamread.orders.stock.exception;

/**
 * Moved here from {@code order.exception} — an unknown SKU is stock's judgement to make, not the
 * order module's. The order module only learns of it because it asked.
 */
public class UnknownSkuException extends RuntimeException {

    private final String sku;

    public UnknownSkuException(String sku) {
        super("Unknown sku: " + sku);
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
