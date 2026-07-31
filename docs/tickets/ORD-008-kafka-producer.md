# ORD-008 — Publish `OrderPlaced` events

**Teaches:** `KafkaTemplate`, serializers, message keys, partitioning, `acks`, async sends

## Problem

Placing an order is invisible to the rest of the world. Nothing downstream can react to it.

## Scope

1. Define the event contract in `com.liamread.orders.order.event`:

   ```
   OrderPlacedEvent(String eventId, String orderId, String customerId,
                    BigDecimal total, String currency, Instant occurredAt)
   ```

   Keep it **separate from `OrderResponse`**. The REST response and the published event are two
   different contracts with two different audiences.

2. Configure the producer in `application.yml`:
   - `key-serializer`: `StringSerializer`
   - `value-serializer`: `JsonSerializer` (from `org.springframework.kafka.support.serializer`)
   - `acks: all`
   - enable idempotence

3. Add `OrderEventPublisher` wrapping `KafkaTemplate<String, OrderPlacedEvent>`:
   - `send(topic, key, event)`, keyed by **`orderId`**
   - handle the returned `CompletableFuture` — log success with partition and offset, log failure
     loudly. Do not ignore the return value.

4. Call it from `OrderService.placeOrder(...)` after the order is saved.

## Acceptance criteria

- [ ] Placing an order via the REST endpoint puts one message on `orders.order-placed.v1`.
- [ ] You can read that message back with the console consumer and it is valid JSON:
      `kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.order-placed.v1 --from-beginning --property print.key=true`
- [ ] The message key is the order id.
- [ ] Two orders for the *same* order id always land on the same partition.
- [ ] Orders with different ids spread across partitions.
- [ ] A send failure (stop the broker mid-request) is logged, not swallowed.

## Things to actually understand

- **The key decides the partition.** Default partitioner: `hash(key) % partitionCount`. Same key →
  same partition → guaranteed ordering *for that key*. A `null` key round-robins and gives you no
  ordering at all. Choosing the key is the single most important design decision when producing.
- **Sends are asynchronous and batched.** `send()` returns immediately with a future; the record sits
  in an in-memory buffer until `linger.ms` or `batch.size` triggers a network call. A "successful"
  method call has not necessarily reached the broker.
- **What `acks` means.**
  - `acks=0` — fire and forget, can lose data silently
  - `acks=1` — leader wrote it; lost if the leader dies before replication
  - `acks=all` — all in-sync replicas have it; slowest and safest
- **Idempotent producer.** A retry after a network timeout can otherwise write the message twice.
  Idempotence makes the broker deduplicate by producer id + sequence number. Effectively free — turn
  it on.
- **`JsonSerializer` writes a type header by default** (`__TypeId__`) telling the consumer which
  Java class to build. Convenient within one codebase, terrible across teams — it couples the
  consumer to your package names. Know that `spring.json.add.type.headers=false` exists and why
  you would use it.

## Design question worth sitting with

You save the order to the database and then publish to Kafka. If the publish fails, the order exists
but nobody knows about it. If you publish first and the save fails, the opposite. This is the
**dual-write problem** — there is no ordering of two systems that makes it atomic. ORD-012 is the
real fix; for now just be aware you have the bug.

## Out of scope

- Consuming the event (ORD-009).
- Transactions and the outbox (ORD-012).
