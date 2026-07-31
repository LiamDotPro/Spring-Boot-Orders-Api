# orders-api — learning roadmap

A backlog of tickets for extending `orders-api`. The goal is **learning Spring Boot fundamentals**, so
each ticket is deliberately small and names the concepts it is meant to teach. Work them roughly in
order — later tickets assume the earlier ones exist.

Flashcards for the concepts live outside this repo in `C:\Users\liam\flashcards`.

## Where the project is today

- `OrderController` — one endpoint, `POST /api/orders-service/order`, takes no input.
- `OrderService` — returns a hardcoded `OrderResponse` (order id `"1"`, £1000, `PENDING`).
- `OrderStatus` — a proper enum with descriptions and an `isTerminal()` helper. Nothing uses it yet.
- No persistence, no validation, no error handling, no tests beyond the context-load test.
- `build.gradle` already declares `spring-kafka`, `spring-kafka-test`, `testcontainers:kafka`,
  actuator and `micrometer-registry-prometheus` — all currently unused.

## Before you start — two bits of housekeeping

1. `build.gradle` declares **both** `spring-boot-starter-webmvc` and `spring-boot-starter-web`.
   These are the new and old names for the same thing. Drop `spring-boot-starter-web` and keep the
   `-webmvc` pair so the main and test starters match.
2. `testcontainers:kafka` has no version and no Testcontainers BOM. It will fail to resolve when you
   first write a test that uses it. Either add `testImplementation
   platform('org.testcontainers:testcontainers-bom:<version>')` or pin the version directly.
   (Handled properly in ORD-011.)

## Tickets

### Core Spring Boot

| Ticket | Title | Teaches |
|---|---|---|
| [ORD-001](tickets/ORD-001-request-binding-and-validation.md) | Accept a real order request body | `@RequestBody`, Bean Validation, DTO ↔ domain mapping |
| [ORD-002](tickets/ORD-002-persistence-with-spring-data-jpa.md) | Persist orders with Spring Data JPA | `@Entity`, `JpaRepository`, `@Transactional`, schema management |
| [ORD-003](tickets/ORD-003-error-handling.md) | Consistent error responses | `@RestControllerAdvice`, `ProblemDetail`, HTTP status mapping |
| [ORD-004](tickets/ORD-004-configuration-and-profiles.md) | Externalise configuration | `@ConfigurationProperties`, profiles, property precedence |
| [ORD-005](tickets/ORD-005-testing.md) | A real test suite | Slice tests vs. full context, `MockMvc`, test fixtures |
| [ORD-006](tickets/ORD-006-observability.md) | Actuator and custom metrics | Health indicators, Micrometer counters/timers |
| [ORD-013](tickets/ORD-013-stock-and-allocation.md) | Real stock, and allocation on accept | A second aggregate, entity relationships, transaction boundaries, concurrent updates |

### Kafka

| Ticket | Title | Teaches |
|---|---|---|
| [ORD-007](tickets/ORD-007-kafka-broker-infrastructure.md) | Run a Kafka broker locally | Brokers, topics, partitions, KRaft, connectivity |
| [ORD-008](tickets/ORD-008-kafka-producer.md) | Publish `OrderPlaced` events | `KafkaTemplate`, serialization, keys, acks |
| [ORD-009](tickets/ORD-009-kafka-consumer.md) | Consume `OrderPlaced` events | `@KafkaListener`, consumer groups, offsets |
| [ORD-010](tickets/ORD-010-kafka-error-handling.md) | Retries and a dead-letter topic | `DefaultErrorHandler`, backoff, poison messages |
| [ORD-011](tickets/ORD-011-kafka-testing.md) | Test the producer and consumer | `@EmbeddedKafka`, Testcontainers, async assertions |
| [ORD-012](tickets/ORD-012-outbox-and-idempotency.md) | Outbox pattern and idempotent consumers | Dual-write problem, at-least-once delivery (stretch) |

## Suggested order

ORD-001 → ORD-003 → ORD-002 → ORD-013 → ORD-005 → ORD-007 → ORD-008 → ORD-009 → ORD-010 → ORD-011 → ORD-004 → ORD-006 → ORD-012

Validation and error handling first because they are quick and change how every later endpoint looks.
Persistence before Kafka because `OrderPlaced` events are much more interesting when there is a real
saved order behind them.
