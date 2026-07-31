# ORD-012 — Outbox pattern and idempotent consumers (stretch)

**Teaches:** the dual-write problem, transactional outbox, at-least-once delivery, deduplication

## Problem

ORD-008 left a real bug in place. `OrderService` does two writes that are not atomic:

```
repository.save(order);          // committed to the database
publisher.send(orderPlaced);     // sent to Kafka
```

If the process dies between them, the order exists and no event was published. Swap the order and you
can publish an event for an order that was never saved. Wrapping both in `@Transactional` does not
help — the database transaction and the Kafka send are separate systems with separate commits.

This is the **dual-write problem**, and it has no solution that involves reordering those two lines.

## Scope

### Part 1 — Transactional outbox

1. Add an `outbox_event` table/entity: `id`, `aggregateId`, `eventType`, `payload` (JSON),
   `createdAt`, `publishedAt` (nullable).
2. In the same database transaction that saves the order, insert the outbox row. One transaction,
   one system, genuinely atomic.
3. Add a `@Scheduled` publisher that polls for unpublished rows, sends them to Kafka, and marks them
   published. Enable it with `@EnableScheduling`.
4. Remove the direct `KafkaTemplate` call from `OrderService`.

### Part 2 — Idempotent consumer

5. The outbox relay can publish a row and crash before marking it published — so it republishes.
   Combined with Kafka's at-least-once delivery, your consumer **will** see duplicates.
6. Add a `processed_event` table keyed by the event's `eventId`. The consumer checks it before
   acting and inserts within the same transaction as the side effect.

## Acceptance criteria

- [ ] Killing the app immediately after the HTTP response still results in the event being published
      once the app restarts.
- [ ] `OrderService` has no reference to `KafkaTemplate`.
- [ ] Delivering the same event twice produces exactly one status transition.
- [ ] The outbox table does not grow without bound — published rows are cleaned up.

## Things to actually understand

- **Why "just use `@Transactional`" fails.** Two resources, two commits. Distributed transactions
  (XA/2PC) technically solve this and are almost universally avoided for good reasons — worth
  understanding what those reasons are.
- **At-least-once is the default, and exactly-once is mostly a lie.** Kafka has exactly-once semantics
  for Kafka-to-Kafka processing, but the moment a side effect touches a database or an external API,
  idempotency on the consumer is the only real answer.
- **The outbox trades latency for correctness.** Polling adds delay. Change-data-capture (Debezium
  reading the write-ahead log) is the low-latency production version of the same idea.
- **Idempotency keys.** This is why `OrderPlacedEvent` carries an `eventId` distinct from `orderId` —
  the event id identifies *this delivery of this fact*, and is what you deduplicate on.

## Gotchas

- `@Scheduled` runs on every instance. Two instances polling the same outbox table will double-publish
  unless you lock the rows (`SELECT ... FOR UPDATE SKIP LOCKED`) or use ShedLock.
- Marking a row published *before* the send completes reintroduces the exact bug you are fixing, one
  level down. Mark after the broker acknowledges.

## Out of scope

- Debezium / CDC.
- Kafka transactions and exactly-once stream processing.
