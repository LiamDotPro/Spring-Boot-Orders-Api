# ORD-002 — Persist orders with Spring Data JPA

**Teaches:** `@Entity`, `JpaRepository`, `@Transactional`, entity vs. DTO, schema management

## Problem

Orders vanish the moment the response is written. There is nothing to look up, nothing to update,
and no status transitions — so `OrderStatus.isTerminal()` has nothing to guard.

## Scope

1. Add `spring-boot-starter-data-jpa` and a database driver. Start with H2 (`runtimeOnly`) so there
   is nothing to install; move to Postgres in a container later if you want the realism.
2. Create `com.liamread.orders.order.OrderEntity` (or `domain/Order`) with:
   - `id` — the order id, a `UUID` or `String`
   - `customerId`, `status` (`@Enumerated(EnumType.STRING)` — see gotcha below), `total`,
     `currency`, `placedAt`
   - a `@OneToMany` collection of order lines with `cascade = ALL` and `orphanRemoval = true`
3. Create `OrderRepository extends JpaRepository<OrderEntity, UUID>`.
4. `OrderService.placeOrder(...)` saves the entity and maps it to `OrderResponse`.
5. Add read and update endpoints:
   - `GET /api/orders-service/order/{id}` → `200` or `404`
   - `GET /api/orders-service/order?customerId=...` → the customer's orders
   - `POST /api/orders-service/order/{id}/cancel` → rejects the transition if
     `status.isTerminal()` is true

## Acceptance criteria

- [ ] A placed order can be fetched back by id after the request completes.
- [ ] The status column stores `PENDING`, not `0`.
- [ ] Cancelling a `DELIVERED` order fails with a sensible 4xx, not a 500 or a silent success.
- [ ] `OrderEntity` never leaves the service layer — the controller only ever sees DTOs.
- [ ] Deriving a query from a method name works (`findByCustomerId`) with no SQL written by hand.

## Things to actually understand

- **How does an interface with no implementation become a working bean?** Spring Data generates a
  proxy at startup and parses the method name into a query. Try `findByCustomerIdAndStatus` and see
  it just work; then misspell a field and watch it fail at *startup*, not at call time.
- **What does `@Transactional` actually do?** It is a proxy that opens a transaction before your
  method and commits after. Two consequences worth internalising: it does nothing when a method is
  called from inside the same class (self-invocation bypasses the proxy), and by default it only
  rolls back on unchecked exceptions.
- **Managed vs. detached entities.** Inside a transaction, changing a loaded entity persists the
  change without any `save()` call. Outside one, it does not. This surprises everyone once.
- **`LazyInitializationException`** — you will hit it the first time you serialise an entity with a
  lazy collection outside a transaction. This is the strongest argument for the DTO boundary.

## Gotchas

- `@Enumerated` defaults to `ORDINAL`, which stores the enum's *position*. Reorder `OrderStatus`
  later and every stored row silently changes meaning. Always use `EnumType.STRING`.
- `spring.jpa.hibernate.ddl-auto=update` is fine for learning and a liability in production. Know
  that Flyway/Liquibase is the real answer even if you do not adopt it here.
- JPA entities need a no-arg constructor, so records cannot be entities. This is why the entity and
  the DTO are genuinely different classes, not just ceremony.

## Out of scope

- Database migrations tooling.
- Publishing events on save (ORD-008, and the dual-write problem in ORD-012).
