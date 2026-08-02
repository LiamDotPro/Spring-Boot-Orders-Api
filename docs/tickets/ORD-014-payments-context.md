# ORD-014 — A payments context that owns its own data

**Teaches:** bounded contexts, event choreography, consumer-owned data, contracts as wire format,
natural-key idempotency, eventual consistency reaching the user

> ## Status: the non-Kafka half is built
>
> Everything that is not Kafka now exists in `com.liamread.orders.payment` — entity, repository,
> service with the decline rules and idempotency, controller, config properties, event records,
> and the two guarded transitions on `OrderService`. Scope items 1–4, 6, 9 and 10 are done.
>
> **What is left for you is exactly the Kafka wiring**, and it is three classes:
>
> | Write | Where | What it does |
> |---|---|---|
> | `PaymentOrderPlacedListener` | `payment.consumer` | `@KafkaListener(groupId = "payments")` → `PaymentService.attemptPayment(...)` |
> | `KafkaPaymentOutcomePublisher` | `payment.event` | implements `PaymentOutcomePublisher` with a `KafkaTemplate` |
> | `PaymentOutcomeListener` | `order.consumer` | `@KafkaListener(groupId = "orders-payment-updates")` → `OrderService.markPaid` / `markPaymentFailed` |
>
> Plus `PaymentTopicConfig` (scope item 7) and the consumer properties (item 11).
>
> `PaymentOutcomePublisher` is the seam: `PaymentService` depends on the interface and a logging
> stand-in is registered while nothing else implements it. Annotate your Kafka version
> `@Component` and it takes over with no other file changed.

## Problem

ORD-009 produced a listener that is architecturally fake, in two separate ways.

**It is a loop-back.** `OrderService` saves an order, publishes `OrderPlaced`, and a listener in the
same process consumes that event to update the same table the same service just wrote. Everything a
broker buys you — durability, fan-out, decoupling — is worth nothing when the producer and the
consumer share a JVM and a database. What you get instead is a race, eventual consistency and a
redelivery failure mode, in exchange for a status change that `placeOrder` could have made on line 58.

**The transition is wrong anyway.** `OrderStatus.PENDING` is documented as *"Order received, awaiting
payment"*, and there is a `PAID` constant sitting between it and `PROCESSING`. Moving `PENDING →
PROCESSING` on `OrderPlaced` claims you are preparing goods for dispatch for an order nobody has paid
for. Nothing in the system currently takes money, so nothing can legitimately move an order past
`PENDING`.

This ticket adds the missing context. Payments becomes a **separate module that owns its own tables**,
hears `OrderPlaced`, decides an outcome, and publishes it. Orders hears that outcome and moves its own
state machine. Neither module touches the other's data, and the events between them are now carrying
information that genuinely did not exist on the other side.

## Design decisions

### 1. A sibling package, not a subpackage

`com.liamread.orders.payment`, alongside `com.liamread.orders.order` — never inside it. The rule that
makes this real:

> Nothing in `com.liamread.orders.payment` may import anything from `com.liamread.orders.order`, and
> vice versa. No `OrderRepository`, no `OrderEntity`, no `OrderStatus`.

That constraint is the entire ticket. The moment you inject `OrderRepository` into `PaymentService` to
"just check something", you have rebuilt the loop-back with extra steps. It is a convention here rather
than something the compiler enforces; if you want it enforced, an ArchUnit test is about six lines and
would be a reasonable addition to ORD-011.

### 2. Each side declares its own copy of the event record

`OrderPlacedEvent` lives in `com.liamread.orders.order.event`. Payments must **not** import it.
Declare a separate record in `com.liamread.orders.payment.event` with the fields payments actually
cares about — `eventId`, `orderId`, `customerId`, `total`, `currency`.

This looks like pointless duplication and is not. Across two repositories you would have no choice, and
being forced into it here teaches the thing worth knowing:

> **The contract is the JSON on the wire, not the class.** The consumer's class is one possible
> reading of a payload it does not own.

It also means payments can ignore fields it does not need, and adding a field to `OrderPlacedEvent`
later does not recompile payments — which is exactly the decoupling you are trying to feel.

The cost is the `__TypeId__` header gotcha from ORD-009: the producer stamps the fully-qualified
producer-side class name onto the record, the consumer looks for that class in its own classpath under
a different package, and deserialization fails at runtime. See Gotchas for the fix. Hitting this
deliberately, once, is worth more than avoiding it.

### 3. Two outcome topics, not one

- `payments.payment-succeeded.v1` — carries a provider reference
- `payments.payment-failed.v1` — carries a decline reason

Topic-per-event-type is what most teams do, the payloads genuinely differ, and it keeps each listener's
intent obvious. The counter-argument is real and worth understanding: two topics means Kafka gives you
**no ordering guarantee between them**, even for the same order key. One `payment-completed` topic with
an `outcome` field would keep every payment fact for an order in one partition, in order. It does not
matter here because one order gets one payment attempt — but notice that "it does not matter here" is a
statement about your domain, not about Kafka.

Follow the existing naming convention and declare the topics in a **payments-owned** config class, not
in `KafkaTopicConfig` — a context owns the topics it publishes.

### 4. Deterministic simulation, not random

You need reproducible runs to test anything. Do not use `Math.random()`. Decline on a rule you can
trigger on demand — a configurable amount ceiling is the cleanest:

```yaml
payments:
  auto-decline-above: 500.00
```

Bind it with `@ConfigurationProperties` (previewing ORD-004) so the threshold is not a magic number,
and so a test can lower it to force a decline. A second forced-failure hook — a `customerId` ending in
`-DECLINE` — is useful for driving failures from Insomnia without changing config.

### 5. `PENDING → PAID` and `PENDING → FAILED` only

The orders side gets two guarded transitions and nothing else. Both must be no-ops from any status
other than `PENDING`, so a duplicate delivery or a cancellation that landed first cannot drag an order
backwards.

## Scope

1. **New package `com.liamread.orders.payment`** with `entity` / `event` / `consumer` subpackages
   mirroring how `order` is laid out.

2. **`PaymentAttempt extends BaseEntity`** — `orderId` (UUID, **unique**), `customerId`, `amount`
   (`precision = 19, scale = 4` to match `OrderEntity.total`), `currency`, `status`
   (`PaymentStatus.PENDING/SUCCEEDED/FAILED`, `@Enumerated(STRING)`), `providerReference` (nullable),
   `failureReason` (nullable), `requestedAt`, `completedAt` (nullable).

   The unique constraint on `orderId` is load-bearing — see the idempotency note below.

3. **`PaymentRepository extends JpaRepository<PaymentAttempt, UUID>`** with `findByOrderId`.

4. **`PaymentService.attemptPayment(...)`**, `@Transactional`:
   - If a `PaymentAttempt` already exists for the order, log and return. Do not charge twice.
   - Otherwise insert one, apply the decline rule, set the outcome, and publish the matching event.

5. **`PaymentOrderPlacedListener`** in the payments package — `groupId = "payments"`, its own copy of
   the event record, logs partition/offset, delegates to `PaymentService`. Same thin shape as ORD-009.

6. **`PaymentEventPublisher`** and two event records (`PaymentSucceededEvent`, `PaymentFailedEvent`),
   both carrying their own `eventId` plus `orderId`, mirroring `OrderPlacedEvent`.

7. **`PaymentTopicConfig`** declaring both new topics, 3 partitions each, in the payments package.

8. **Orders-side listener** — `PaymentOutcomeListener` in `com.liamread.orders.order.consumer`,
   `groupId = "orders-payment-updates"`, one method per topic, each delegating to a new guarded
   `OrderService` method (`markPaid` / `markPaymentFailed`).

9. **`GET /api/payments/{orderId}`** returning the attempt as a DTO, `404` if none. Without this you
   cannot observe the payments side without opening a SQL client.

10. **Remove the `PENDING → PROCESSING` transition from ORD-009's listener.** Keep the audit listener —
    it still demonstrates independent consumer groups, which is what it was for. See "Changes to
    existing tickets".

11. **Consumer config for the new listeners.** They share the `spring.kafka.consumer` block from
    ORD-009; the type-header problem in Gotchas is the only new configuration.

## Acceptance criteria

- [ ] `POST /api/orders-service/order` returns `201` with status `PENDING`, and a `GET` of the same
      order a moment later shows `PAID` — with no second HTTP call having caused it.
- [ ] `GET /api/payments/{orderId}` shows a `SUCCEEDED` attempt with a provider reference.
- [ ] An order over `payments.auto-decline-above` ends as `FAILED` with a decline reason on the
      payment attempt, and the order is never `PAID`.
- [ ] Nothing in `com.liamread.orders.payment` imports from `com.liamread.orders.order`. Check by
      searching the imports; this is the criterion that actually matters.
- [ ] Replaying the `orders.order-placed.v1` topic into the `payments` group (reset the offsets, or use
      a fresh `groupId`) produces **no** new payment attempts and no duplicate charges.
- [ ] `kafka-consumer-groups.sh --describe` lists `orders-fulfilment`, `orders-audit`, `payments` and
      `orders-payment-updates` as four independent groups with their own offsets.
- [ ] Stopping the app, placing an order, and restarting it results in the order still reaching `PAID`
      once the consumer catches up.
- [ ] Cancelling an order before payment lands leaves it `CANCELLED`, not `PAID`.

## Things to actually understand

- **Choreography vs orchestration.** Nothing here is a coordinator. Orders does not tell payments to
  charge; it announces a fact and payments decides what that means to it. This scales beautifully and
  debugs horribly — no single place describes the flow, which is why the audit listener and good
  logging matter more in event-driven systems than in a call stack.

- **Consumer-owned data is the whole point.** The ORD-009 listener was fake because it wrote to the
  producer's table. This one is real because `payment_attempt` is data that did not exist anywhere
  until payments created it, and orders cannot read it except by asking.

- **Idempotency by natural key.** The unique constraint on `orderId` means a redelivered `OrderPlaced`
  cannot produce a second charge — the database refuses. Note how much stronger this is than the
  status guard: it survives two consumers processing the duplicate *concurrently*, which a
  read-then-check does not. ORD-012's `processed_event` table is the general version of the same
  idea; this is the domain-specific one, and it is better when it is available.

- **Eventual consistency is now visible to your users.** The `201` says `PENDING` and the truth becomes
  `PAID` some milliseconds later. There is no longer a moment where "the order" has one correct state
  across the system. That is not a bug to fix — it is the trade you made — but it means clients must
  poll or subscribe, and "read your own write" stops being free.

- **The trust boundary.** Payments takes the amount from the event and charges it. A real payment
  service re-derives or verifies the amount rather than trusting a number an upstream service put in a
  message, because anything that can publish to the topic can now name its own price. Worth noticing
  even though you should not build the verification here.

- **Failure of the business operation is not failure of the message.** A declined card is a perfectly
  successful piece of message processing — the listener must return normally and commit the offset.
  Throwing would redeliver the event and retry a card that will decline again. Distinguishing "this
  message could not be processed" from "this message was processed and the answer was no" is the
  single most common bug in event-driven code.

## Gotchas

- **`__TypeId__` mismatch.** `JacksonJsonSerializer` writes the producer's FQCN into a header;
  `JacksonJsonDeserializer` will try to load `com.liamread.orders.order.event.OrderPlacedEvent` when
  deserializing in the payments listener, and your payments-side record has a different name. Two
  fixes, both instructive: set `spring.json.value.default.type` on the consumer, or configure type
  mappings (`spring.json.type.mapping`) so a logical name like `orderPlaced` maps to each side's own
  class. The second is what you would do in production — the wire carries a name both sides agree on,
  not a Java package.
  Since you now have **two different payload types on two different topics** arriving at listeners in
  one app, a single `value.default.type` is not enough. Prefer type mappings, or give each listener its
  own container factory.

- **Same JVM is not same transaction.** `PaymentService` runs on a Kafka listener thread with its own
  transaction, and can still lose the race against `placeOrder`'s commit. It does not read the orders
  table so it will not notice — but the orders-side `PaymentOutcomeListener` can, if payment is very
  fast. If `markPaid` finds no order, that is the ORD-012 dual-write problem, not a bug in your code.

- **Four listeners, four `groupId`s.** Two listeners sharing a `groupId` on one topic *split* the
  partitions and each sees roughly a third of the messages. Copy-pasting a listener and forgetting to
  change the group is the classic way to make half your events silently vanish.

- **`ddl-auto: update` will never add the unique constraint** to `payment_attempt` if you add the
  annotation after the table exists. Get it right first, or drop the table.

- **Currency is not checked anywhere.** Payments will happily "charge" 500 of whatever
  `OrderPlacedEvent.currency` says. `CurrencyOptions` exists; use it, or write down why you did not.

- **`BigDecimal` comparison** against the threshold: use `compareTo`, not `equals`, and decide whether
  the boundary itself declines.

## Changes to existing tickets

- **ORD-009** — its acceptance criterion *"Placing an order updates its status to `PROCESSING`"* is
  superseded. The consumer-group, offset, rebalancing and lag criteria all stand, and are now
  demonstrated by four real groups instead of two artificial ones.
- **ORD-013** — its lifecycle (`accept` → `ALLOCATED`) now has a sensible predecessor: allocate stock
  for orders that reached `PAID`, rather than for orders that were merely placed.

## Out of scope

- A real payment provider, or any HTTP call out. The simulation is the point.
- Refunds, partial payments, retries of a declined card.
- Retry/DLT behaviour on the new listeners (ORD-010 covers it and applies here unchanged).
- The outbox — `PaymentService` has exactly the same dual-write bug as `OrderService` (ORD-012).
- Extracting payments into its own process. When you want that, the boundary this ticket draws is
  what makes it a build change rather than a rewrite.
- A fulfilment context consuming `PaymentSucceeded`. That is the natural next ticket.
