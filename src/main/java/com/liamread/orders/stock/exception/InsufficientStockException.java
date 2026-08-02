package com.liamread.orders.stock.exception;

/**
 * Not enough available to reserve what was asked for.
 *
 * <p>A 409 rather than a 400: nothing about the request was malformed, and the identical request
 * would succeed against the same SKU once a restock lands. Carries the numbers as fields so the
 * handler can put them in the response body without the client parsing prose.
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for " + sku + ": requested " + requested + ", available " + available);
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
