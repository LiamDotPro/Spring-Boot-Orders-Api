# ORD-007 — Run a Kafka broker locally

**Teaches:** brokers, topics, partitions, replication factor, KRaft mode, client connectivity

## Problem

`spring-kafka` is on the classpath but there is no broker to talk to and no topic to talk about.
Before writing a producer, you need something you can inspect from the command line — otherwise
debugging ORD-008 is guesswork.

## Scope

1. Add `docker-compose.yml` at the project root running a single Kafka broker in **KRaft mode**
   (no Zookeeper — modern Kafka does not need it). Expose `9092` to the host.
2. Add the connection config to `application.yml`:
   ```yaml
   spring:
     kafka:
       bootstrap-servers: localhost:9092
   ```
3. Declare the topic from the application rather than creating it by hand. A `@Bean` returning a
   `NewTopic` (built with `TopicBuilder`) is picked up by Spring's `KafkaAdmin` and created at
   startup. Name it `orders.order-placed.v1`.
   - `partitions`: 3 — so partitioning and ordering are actually observable later
   - `replicas`: 1 — you only have one broker
4. Optionally add a UI container (kafka-ui / redpanda-console) on a host port so you can browse
   topics and messages in a browser.

## Acceptance criteria

- [ ] `docker compose up -d` gives a broker reachable on `localhost:9092`.
- [ ] Starting the Spring app creates `orders.order-placed.v1` automatically with 3 partitions.
- [ ] You can list topics and describe that topic from a shell inside the broker container
      (`kafka-topics.sh --bootstrap-server localhost:9092 --list` / `--describe --topic ...`).
- [ ] `GET /actuator/health` shows a Kafka component once ORD-006 is done.
- [ ] Stopping the broker and starting the app produces a clear connection error you recognise.

## Things to actually understand

- **Topic → partitions → offsets.** A topic is a name. The partition is the actual ordered, append-only
  log. An offset is a message's position within one partition. Ordering is guaranteed *per partition*
  and nowhere else. Almost every Kafka misunderstanding traces back to this sentence.
- **Why 3 partitions?** Partitions are the unit of parallelism. A consumer group can have at most one
  consumer per partition doing useful work — 3 partitions means at most 3 active consumers.
- **Retention is not a queue.** Consuming does not delete the message. It stays for the retention
  period and any number of independent consumer groups can read it. This is the biggest difference
  from RabbitMQ/SQS.
- **`advertised.listeners` is the single most common local-setup trap.** The broker tells clients
  which address to reconnect on. If it advertises its container hostname, your app on the host
  connects once, gets told to talk to `kafka:9092`, and hangs. Configure a host-facing listener.

## Naming note

`orders.order-placed.v1` — `<domain>.<event>.<version>`. Versioning in the topic name gives you an
escape hatch when the event schema changes incompatibly, since you cannot rewrite messages already
in the log.

## Out of scope

- Multi-broker clusters, replication, schema registry.
- Anything the application actually sends (ORD-008).
