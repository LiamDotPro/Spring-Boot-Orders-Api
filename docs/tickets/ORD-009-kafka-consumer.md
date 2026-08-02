# ORD-009 — Consume `OrderPlaced` events

**Teaches:** `@KafkaListener`, consumer groups, offsets, rebalancing, deserialization, concurrency

## Problem

Events are being produced and nothing reads them. This is the half of Kafka where all the
interesting behaviour lives.

## Scope

1. Add a consumer in `com.liamread.orders.order.consumer`:

   ```java
   @KafkaListener(topics = "...", groupId = "orders-fulfilment")
   public void onOrderPlaced(OrderPlacedEvent event) { ... }
   ```

   Give it something to do that is observably real — move the order from `PENDING` to `PROCESSING`
   via the repository from ORD-002, and log the partition and offset it came from.

   > **This side effect is deliberately artificial and ORD-014 replaces it.** A service publishing an
   > event so it can consume its own event and mutate its own aggregate is a loop-back — it buys
   > nothing and costs you a race. It is here because the consumer-group lessons below need a side
   > effect you can observe over HTTP. Guard the transition on the order being `PENDING` so it does
   > not fight ORD-013's lifecycle, and do not take it as a design recommendation.

2. Configure the consumer in `application.yml`:
   - `key-deserializer`: `StringDeserializer`
   - `value-deserializer`: `JsonDeserializer`, wrapped in `ErrorHandlingDeserializer`
   - `spring.json.trusted.packages`: `com.liamread.orders.*` (it will not deserialize otherwise)
   - `auto-offset-reset: earliest`
   - `enable-auto-commit: false`

3. Log the full metadata by taking extra parameters on the listener method —
   `@Header(KafkaHeaders.RECEIVED_PARTITION) int partition`,
   `@Header(KafkaHeaders.OFFSET) long offset` — or by accepting a `ConsumerRecord<String, OrderPlacedEvent>`.

4. Add a **second** listener with a *different* `groupId` (e.g. `orders-audit`) that just logs. This
   is the single most instructive five minutes in this whole ticket.

## Acceptance criteria

- [ ] Placing an order updates its status to `PROCESSING` without the HTTP request doing it.
- [ ] Both consumer groups receive **every** message independently.
- [ ] Restarting the app does not reprocess already-committed messages.
- [ ] A brand-new `groupId` reads the topic from the beginning.
- [ ] Running two instances in the same group with `concurrency = 3` shows partitions being split
      between them, and a rebalance in the logs when one stops.
- [ ] `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group orders-fulfilment`
      shows current offset, log-end offset and lag.

## Things to actually understand

- **A consumer group is the unit of "who has read what".** Offsets are committed per
  (group, topic, partition). Two groups on one topic each get a full copy — that is how you add a new
  consumer without disturbing existing ones.
- **Within a group, each partition goes to exactly one consumer.** More consumers than partitions and
  the extras sit idle. This is why partition count caps your parallelism.
- **`auto-offset-reset` only applies when there is no committed offset** — a new group, or one whose
  offsets expired. It is not "always start from the beginning", which is why changing it appears to
  do nothing on an existing group.
- **`@KafkaListener` methods should be fast and idempotent.** Kafka gives at-least-once delivery by
  default: a redelivery after a rebalance or a failed commit means your handler will see the same
  event twice. Handle it (ORD-012), do not hope.
- **Rebalancing.** When a consumer joins or leaves, partitions are reassigned and processing pauses.
  A handler that takes longer than `max.poll.interval.ms` gets its consumer kicked out of the group —
  which then triggers another rebalance, and you have a livelock. Classic production incident.
- **`concurrency`** on the listener container creates N threads, each polling its own partitions —
  parallelism within one JVM, still bounded by partition count.

## Gotchas

- `JsonDeserializer` without trusted packages configured throws at runtime, not startup. The error
  message is clear once you have seen it once.
- A deserialization failure is **not** recoverable by retrying — the bytes will never parse. Without
  `ErrorHandlingDeserializer` in front, the container retries the same poison record forever and the
  partition stops dead. That is what ORD-010 fixes.
- If the producer sets `__TypeId__` headers and the consumer's package names differ, deserialization
  fails. `spring.json.value.default.type` is the escape hatch.

## Out of scope

- Retries and dead-letter topics (ORD-010).
- Testing (ORD-011).
