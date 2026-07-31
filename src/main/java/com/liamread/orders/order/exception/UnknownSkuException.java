package com.liamread.orders.order.exception;

public class UnknownSkuException extends RuntimeException {
    public UnknownSkuException(String sku) {
        super("Unknown sku: " + sku);
    }
}