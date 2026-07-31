# ORD-003 — Consistent error responses

**Teaches:** `@RestControllerAdvice`, `@ExceptionHandler`, `ProblemDetail` (RFC 9457), status mapping

## Problem

Every failure currently produces Spring's default error body, and any exception the service throws
becomes a 500. A client cannot tell "you sent bad data" from "we broke".

## Scope

1. Add domain exceptions in `com.liamread.orders.order.exception`:
   - `OrderNotFoundException` → 404
   - `InvalidStatusTransitionException` → 409 Conflict
2. Add a `GlobalExceptionHandler` annotated `@RestControllerAdvice` with `@ExceptionHandler` methods
   returning `ProblemDetail`.
3. Handle `MethodArgumentNotValidException` (from ORD-001) so validation failures return a 400 whose
   body lists which fields failed and why.
4. Add a catch-all `Exception` handler that returns a generic 500 — and **logs the stack trace**
   rather than putting it in the response.

## Acceptance criteria

- [ ] `GET /order/{unknown-id}` returns 404 with a `ProblemDetail` body (`type`, `title`, `status`,
      `detail`).
- [ ] Cancelling a terminal order returns 409, not 500.
- [ ] A validation failure returns 400 and names the offending fields.
- [ ] No response body ever contains a stack trace or a database error message.

## Things to actually understand

- **`@RestControllerAdvice` is `@ControllerAdvice` + `@ResponseBody`** — the same relationship as
  `@RestController` to `@Controller` (see flashcard 01).
- **Handler selection is by most-specific type.** A handler for `RuntimeException` and one for
  `OrderNotFoundException` can coexist; the narrower one wins. Verify that rather than assuming it.
- **`ProblemDetail` is the standard error shape** (RFC 9457, `application/problem+json`) and is built
  into Spring Framework 6+. Prefer it over inventing your own `ErrorResponse` record.
- **Where the boundary sits.** Nothing below the controller layer should know about HTTP status
  codes. The service throws a domain exception; the advice decides it means 409.

## Gotchas

- An exception thrown from a filter or from `HandlerInterceptor` never reaches
  `@RestControllerAdvice` — different point in the chain.
- `@ResponseStatus` on an exception class is the quick version of this, but it cannot build a body.
  Try both so you know why the advice is worth the extra class.

## Out of scope

- Kafka consumer error handling — that is a completely different mechanism (ORD-010).
