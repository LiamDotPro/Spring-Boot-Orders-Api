# ORD-001 — Accept a real order request body

**Teaches:** `@RequestBody`, Bean Validation, DTO vs. domain model, `@Valid` failure handling

## Problem

`POST /api/orders-service/order` takes no input and `OrderService.placeOrder()` returns a hardcoded
`OrderResponse`. An order that is identical every time is not an order.

## Scope

Introduce a request DTO and make the response derive from it.

1. Add `PlaceOrderRequest` in `com.liamread.orders.order.dto` — a record with at least:
   - `customerId` — required, non-blank
   - `items` — required, at least one element; each item has a `sku` (non-blank) and `quantity`
     (at least 1)
   - `currency` — required, 3 letters (a good use for `@Pattern`)
2. Annotate the fields with `jakarta.validation` constraints (`@NotBlank`, `@NotNull`, `@NotEmpty`,
   `@Min`, `@Pattern`, `@Valid` on the nested list).
3. Change the controller signature to
   `placeOrder(@Valid @RequestBody PlaceOrderRequest request)`.
4. `OrderService.placeOrder(PlaceOrderRequest)` generates an order id (`UUID.randomUUID()`),
   calculates a total from the items, sets `OrderStatus.PENDING` and stamps `Instant.now()`.

## Acceptance criteria

- [ ] A valid POST returns `202 Accepted` with a body reflecting the submitted order.
- [ ] A request missing `customerId` returns `400`, **not** `500`.
- [ ] A request with `quantity: 0` returns `400`.
- [ ] A request with an empty `items` array returns `400`.
- [ ] Prices are `BigDecimal` throughout. No `double` anywhere near money.

## Things to actually understand

- **Why is a separate request DTO better than reusing one class for request, response and storage?**
  Think about what happens when a client can POST an `orderId` or a `status`.
- **What makes `@Valid` fire?** The annotation on the controller parameter is what triggers
  validation. Without it the constraints on the record are inert. Try removing it and see.
- **Where does the 400 come from?** `@Valid` failing throws `MethodArgumentNotValidException`, which
  Spring's default handler turns into a 400. ORD-003 is about taking control of that response body.
- **`@Valid` vs. `@Validated`** — one is the Jakarta standard, one is Spring's, and only one supports
  validation groups.

## Hints

- You need `spring-boot-starter-validation` on the classpath. It is not pulled in by the web starter.
- Nested objects are not validated unless the field holding them is itself annotated `@Valid` —
  e.g. `List<@Valid OrderItem> items`.
- Records work fine with constraint annotations; put them directly on the components.

## Out of scope

- Persisting the order (ORD-002).
- Customising the error body (ORD-003).
- Publishing an event (ORD-008).
