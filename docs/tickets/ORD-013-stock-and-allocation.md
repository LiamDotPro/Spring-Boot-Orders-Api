# ORD-013 — Real stock in the database, and allocation on accept

**Teaches:** owning a second aggregate, entity relationships, transaction boundaries across services,
concurrent updates to a shared row, state machines

## Problem

`PriceCatalog` is a hardcoded `Map` of three items pretending to be a warehouse. Its `CatalogueItem`
even has a `quantity` field — which means *stock on hand*, not "how many the customer asked for" —
and that record is returned verbatim inside `OrderResponse`. So the API currently tells a customer
who ordered 2 shelves that their order line is `quantity: 11`.

Nothing decrements. Two customers can both order the last wardrobe and both succeed. There is no
difference between "we took your order" and "we have set your goods aside", so there is nothing for
an accept or a finalize step to actually *do*.

This ticket gives stock a table, an owner, and the three numbers that make the order lifecycle
mean something.

## The central idea: available is derived, not stored

Every real inventory system tracks two numbers per SKU and computes the third:

| Field | Meaning |
|---|---|
| `quantityOnHand` | Physically sat in the warehouse. Only changes when goods move. |
| `quantityAllocated` | Reserved for accepted orders that have not shipped. |
| `quantityAvailable` | `onHand - allocated`. **Never stored** — always computed. |

The order lifecycle then maps onto arithmetic:

| Action | Effect on stock | Order status |
|---|---|---|
| Place | none — we have only priced it | `PENDING` |
| **Accept** | `allocated += qty`, guarded by `available >= qty` | `ALLOCATED` |
| **Finalize** | `onHand -= qty`, `allocated -= qty` | `SHIPPED` |
| Cancel (from `PENDING`) | none | `CANCELLED` |
| Cancel (from `ALLOCATED`) | `allocated -= qty` (release) | `CANCELLED` |

Note that finalize decrements *both*, so `available` is unchanged by shipping — the goods were
already spoken for. That invariant (`available` only moves on accept, cancel and restock) is the
thing to check yourself on.

## Design decisions

### 1. One `StockItem` entity, not `Product` + `StockLevel` — for now

Pricing and inventory are genuinely different concerns: price changes when marketing says so, stock
changes when a forklift moves. The textbook answer splits them into two tables.

**Do not split them yet.** One `StockItem` row per SKU carrying both description/price and the two
quantities keeps this ticket to one repository and one lock. Split when either becomes true: you
need price history (a price is a row with a valid-from date, not a column), or stock lives per
warehouse (`stock_level` gains a `location_id` and stops being one-row-per-SKU).

### 2. Stock owns price, and the order snapshots it

`StockService` is the only thing that reads `unitPrice`. `OrderService` asks it once, at place time,
and **copies the price onto the order line**. It never looks it up again.

This is not an optimisation — it is correctness. If price lived only in the stock table, repricing a
bookcase tomorrow would silently rewrite the total of every order placed today, including shipped
ones. An order line records what was agreed, and that is immutable.

### 3. Orders finally get persisted lines

Allocation needs to know what to allocate on accept, which means the SKUs and quantities have to
survive the request. `OrderEntity` needs the `@OneToMany` collection ORD-002 asked for and never got:

```
OrderLine: sku, quantity, unitPrice (snapshot), lineTotal (or derived)
```

`OrderEntity.total` becomes the sum of the lines rather than a number computed in the service and
trusted forever.

### 4. `OrderService` calls `StockService` in-process, synchronously

One `@Transactional` method on `OrderService.accept(orderId)` that calls into `StockService`,
inside the same transaction. Not events, not Kafka — yet.

That call is the seam. When ORD-008/ORD-012 arrive and stock moves to its own service, this is the
line that becomes a message and the all-or-nothing transaction becomes a saga with a compensating
release. Write it as a plain method call now so you can feel what the transaction was buying you
when you take it away.

### 5. `ALLOCATED` is a new status, not a reuse of `PROCESSING`

`OrderStatus` already has eight constants and none of them mean "stock is reserved". Add
`ALLOCATED("Stock reserved, awaiting dispatch")` between `PAID` and `PROCESSING`.

Resist the urge to reorder the enum while you are in there — see the `@Enumerated` gotcha in ORD-002.
It is `EnumType.STRING`, so you are actually safe, which is exactly why it is worth noticing that
you would not have been otherwise.

## Scope

1. **New package `com.liamread.orders.stock`.** Delete the stub at
   `stock.dto.exception.StockController` — that path is three levels of wrong (a controller in a
   `dto.exception` package).
2. **`StockItem extends BaseEntity`** — `sku` (unique, indexed), `description`, `unitPrice`
   (`precision = 19, scale = 4` to match `OrderEntity.total`), `quantityOnHand`, `quantityAllocated`.
   Add `getQuantityAvailable()` as a plain computed getter, `@Transient` so JPA ignores it.
3. **`StockRepository extends JpaRepository<StockItem, UUID>`** with `findBySku(String sku)` and the
   locked/conditional variants from the concurrency section below.
4. **`StockService`** with the operations the order flow needs:
   - `lookup(sku)` → price + description, for pricing at place time
   - `allocate(sku, qty)` → fails if `available < qty`
   - `release(sku, qty)` → cancel path
   - `consume(sku, qty)` → finalize path
   - `restock(sku, qty)` → so you can put goods in without SQL
5. **`StockController`** at `/api/stock`:
   - `GET /api/stock` — list, showing all three quantities
   - `GET /api/stock/{sku}` — one, `404` if unknown
   - `POST /api/stock/{sku}/restock` — body `{ "quantity": 10 }`
6. **Order lines persisted** — `OrderLine` entity, `@OneToMany(cascade = ALL, orphanRemoval = true)`
   on `OrderEntity`.
7. **Order lifecycle endpoints:**
   - `POST /api/orders-service/order/{id}/accept` → allocates every line, or none
   - `POST /api/orders-service/order/{id}/finalize` → consumes every line
   - `POST /api/orders-service/order/{id}/cancel` → releases if it was allocated
8. **Seed data** — a `data.sql` with the three existing SKUs so the app is usable from a cold
   database. Keep the same sku/description/price values so your Insomnia requests still work.
9. **Delete `com.liamread.orders.order.pricing`** entirely and stop returning `CatalogueItem` from
   `OrderResponse` — replace it with a `OrderLineResponse(sku, description, quantity, unitPrice,
   lineTotal)` where `quantity` means *what the customer ordered*.
10. **New exceptions** — `InsufficientStockException` (→ 409 Conflict) and
    `InvalidOrderStateException` (→ 409). `UnknownSkuException` stays but moves package.

## Fix while you are in here

`OrderService.placeOrder` has three bugs this refactor should not carry forward:

- It returns `UUID.randomUUID()` instead of the saved entity's id, so the caller can never fetch
  their order back. Save first, then read `entity.getId()`.
- It hardcodes `"Cus-1"` and ignores `requestInfo.customerId()`, which validation already guarantees
  is present.
- `CatalogueItem` uses `@NotBlank` on an `Integer` and a `BigDecimal`. `@NotBlank` is string-only —
  it will throw at validation time, not fail to compile. Use `@NotNull` / `@Positive`. The
  replacement records should not inherit this.

## Concurrency — the part that is actually interesting

Two requests accept two different orders for the last wardrobe at the same moment. Both read
`available = 1`, both decide it is fine, both write `allocated = 1`. You have now promised one
wardrobe to two customers, and no exception was thrown.

Three ways out, in increasing order of how much you should like them:

1. **Optimistic locking.** `BaseEntity` already has `@Version`, so the second writer's `UPDATE ...
   WHERE version = ?` matches zero rows and Hibernate throws `OptimisticLockException`. Correct, but
   the loser gets an error and you have to decide whether to retry.
2. **Pessimistic locking.** `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a `findBySku` query emits
   `SELECT ... FOR UPDATE`; the second request blocks until the first commits, then reads the true
   value. Simple to reason about, serialises everyone through one row.
3. **A conditional update** — the one to reach for:
   ```sql
   UPDATE stock_item
      SET quantity_allocated = quantity_allocated + :qty
    WHERE sku = :sku
      AND quantity_on_hand - quantity_allocated >= :qty
   ```
   The database does the check and the write atomically. Spring Data returns the affected row count
   from an `@Modifying` query — `0` means insufficient stock, `1` means allocated. No lock held
   across your application logic, no read-then-write gap at all.

Whichever you pick, **allocate a multi-line order's SKUs in a deterministic order** (sort by sku).
Order A locking SKU-1 then SKU-2 while order B locks SKU-2 then SKU-1 is a textbook deadlock, and
Postgres will resolve it by killing one of them.

## Acceptance criteria

- [ ] `GET /api/stock/SKU-2` returns on-hand, allocated and available, and available is not a column
      in the database.
- [ ] Placing an order changes no stock quantity at all.
- [ ] Accepting an order raises `quantityAllocated` and leaves `quantityOnHand` untouched.
- [ ] Finalizing lowers both by the same amount, leaving `available` unchanged.
- [ ] Accepting an order for more than is available returns `409` and allocates **nothing** — not
      even the lines that would have fit.
- [ ] A two-line order where the second line is short leaves the first line's stock unallocated.
      (This is the transaction boundary earning its keep. Verify it by looking at the table, not the
      response.)
- [ ] Accepting the same order twice returns `409` the second time and does not double-allocate.
- [ ] Cancelling an `ALLOCATED` order returns the stock to available; cancelling a `PENDING` one
      changes no quantities.
- [ ] Finalizing a `PENDING` order is rejected — you cannot ship what was never reserved.
- [ ] Changing a SKU's price does not change the total of an order already placed.

## Things to actually understand

- **Why "available" must not be a column.** The moment you store it you have two sources of truth
  that can disagree, and every bug becomes "which of the three numbers is lying?". Derived state is
  free to recompute and impossible to corrupt. The counter-argument is querying — "find everything
  with available < 5" cannot use an index on a computed getter. Postgres generated columns are the
  real answer if you ever need it.
- **Where the transaction begins and ends.** `accept()` is one `@Transactional` method that touches
  N stock rows and one order row. Every one of those writes commits or none does. Notice that this
  is the *only* reason partial allocation is impossible — remove the annotation and the tests above
  start failing in ways that look like race conditions but are not.
- **Self-invocation still bites.** If `accept()` calls a private `allocateLine()` in the same class
  annotated `@Transactional(REQUIRES_NEW)`, the annotation does nothing — the proxy is bypassed.
  ORD-002 mentioned this; here you have a realistic reason to reach for it and be wrong.
- **Why status is the idempotency guard.** There is no separate "already accepted?" check. The
  transition `PENDING → ALLOCATED` is only legal from `PENDING`, and that check plus the update
  happen in the same transaction, so a duplicate request either sees `PENDING` and wins or sees
  `ALLOCATED` and is rejected. State machines are idempotency, done cheaply.
- **Rows affected as a return value.** `@Modifying` queries returning `int` is the pattern behind
  most lock-free concurrency in ordinary CRUD apps. `if (updated == 0) throw` is doing real work.

## Gotchas

- `@OneToMany` defaults to `LAZY`, so serialising an `OrderEntity` with lines outside a transaction
  throws `LazyInitializationException` — `spring.jpa.open-in-view=false` is already set, which makes
  this happen immediately rather than mysteriously in production. Map to DTOs inside the service.
- Loading an order and its lines in a list endpoint is the N+1 query problem. `OrderListResponse`
  already dodges it by having no lines; keep it that way, and use a `JOIN FETCH` for the single-order
  read.
- `BigDecimal` equality: `new BigDecimal("10.00").equals(new BigDecimal("10.0"))` is `false`. Use
  `compareTo` in assertions or you will write a passing test that proves nothing.
- `ddl-auto=update` will add the new tables but will never add the unique constraint on `sku` if you
  add the annotation later. Drop the schema when you change constraints, or accept that your local
  database has drifted from your entities.
- Don't put `quantityAllocated` on the order line as well as the stock row. The order line's
  `quantity` plus the order's status already tells you whether it is allocated. A second copy is a
  second thing to keep in sync.

## Out of scope

- Multiple warehouses / locations.
- Backorders and partial fulfilment — an order is fully allocatable or it is rejected.
- Price history and effective dates.
- Publishing `StockAllocated` / `OrderAccepted` events (ORD-008), and the compensating-transaction
  version of accept when stock is a separate service (ORD-012).
- Auth on the restock endpoint, which is obviously an admin operation.
