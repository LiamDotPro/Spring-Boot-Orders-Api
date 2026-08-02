package com.liamread.orders.common;

import com.liamread.orders.order.exception.InvalidStatusTransitionException;
import com.liamread.orders.order.exception.OrderAccessDeniedException;
import com.liamread.orders.order.exception.OrderNotFoundException;
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
