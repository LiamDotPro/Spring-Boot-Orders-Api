package com.liamread.orders.common;

import com.liamread.orders.order.exception.InvalidStatusTransitionException;
import com.liamread.orders.order.exception.OrderAccessDeniedException;
import com.liamread.orders.order.exception.OrderNotFoundException;
import com.liamread.orders.payment.exception.PaymentNotFoundException;
import com.liamread.orders.stock.exception.InsufficientStockException;
import com.liamread.orders.stock.exception.UnknownSkuException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates domain exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>This is the only place in the application that decides what a failure means in HTTP terms.
 * Services throw exceptions named for the domain rule that was broken; the mapping to a status
 * code happens here, at the web boundary.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — the id is well-formed but matches nothing. A malformed id never reaches here: Spring
     * fails to convert it to a {@code UUID} and returns 400 before the handler method is invoked.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Order not found");
        problem.setType(URI.create("https://api.liamread.com/errors/order-not-found"));
        return problem;
    }

    /**
     * 409 — the request was valid and permitted, but conflicts with the order's current state.
     * Deliberately not 400: nothing about the request was wrong, and the same request would succeed
     * against the same order in an earlier state.
     *
     * <p>Both statuses are echoed as extension members so a client can branch on them. RFC 9457
     * allows arbitrary top-level fields alongside the standard ones, which is why
     * {@code setProperty} exists.
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid status transition");
        problem.setType(URI.create("https://api.liamread.com/errors/invalid-status-transition"));
        problem.setProperty("currentStatus", ex.getFrom());
        problem.setProperty("requestedStatus", ex.getTo());
        return problem;
    }

    /**
     * 404 — no payment has been attempted for this order.
     *
     * <p>Worth noticing that this is a routine, temporary answer rather than a fault: between an
     * order being placed and its {@code OrderPlaced} event being consumed, this is the correct
     * response. A client polling for a payment should treat 404 as "not yet", not as "never".
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Payment not found");
        problem.setType(URI.create("https://api.liamread.com/errors/payment-not-found"));
        return problem;
    }

    /**
     * 404 — the SKU is not in the catalogue at all. A 400 would be defensible, but the request is
     * well-formed and names a resource; it is the resource that does not exist.
     */
    @ExceptionHandler(UnknownSkuException.class)
    public ProblemDetail handleUnknownSku(UnknownSkuException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Unknown SKU");
        problem.setType(URI.create("https://api.liamread.com/errors/unknown-sku"));
        problem.setProperty("sku", ex.getSku());
        return problem;
    }

    /**
     * 409, not 400 — nothing was wrong with the request, and the identical request would succeed
     * once a restock lands. The numbers go out as extension members so a client can show "only 2
     * left" without parsing the sentence.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Insufficient stock");
        problem.setType(URI.create("https://api.liamread.com/errors/insufficient-stock"));
        problem.setProperty("sku", ex.getSku());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        return problem;
    }

    /**
     * 403, not 401: the caller is identified, they simply do not own this order. A 401 would claim
     * the request was unauthenticated and is required to carry a {@code WWW-Authenticate} header.
     *
     * <p>The response body says nothing about who does own the order, or that it exists at all —
     * the specifics go to the log, where they are useful and not attacker-readable.
     */
    @ExceptionHandler(OrderAccessDeniedException.class)
    public ProblemDetail handleOrderAccessDenied(OrderAccessDeniedException ex) {
        log.warn("Rejected cross-customer access: customer={} order={}", ex.getCustomerId(), ex.getOrderId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have access to this order.");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://api.liamread.com/errors/order-access-denied"));
        return problem;
    }
}
