# ORD-011 — Test the producer and consumer

**Teaches:** `@EmbeddedKafka`, Testcontainers, async assertions, isolating tests from a broker

## Problem

After ORD-008 every `@SpringBootTest` tries to reach a real broker. The suite is now slow, flaky, or
broken depending on whether Docker happens to be running. Messaging code also fails in ways unit
tests cannot catch — serialization mismatches, wrong topic names, misconfigured trusted packages.

## Scope

1. **Fix the dependency first.** `testImplementation 'org.testcontainers:kafka'` in `build.gradle`
   has no version. Add the Testcontainers BOM:
   ```gradle
   testImplementation platform('org.testcontainers:testcontainers-bom:<version>')
   ```
   or pin the version explicitly. It will not resolve as written.

2. **Producer test** — `@SpringBootTest` + `@EmbeddedKafka(partitions = 3, topics = "...")`. Call
   `OrderService.placeOrder(...)`, then use a test `Consumer` (via `KafkaTestUtils`) to read the
   record back. Assert on the key, the topic and the deserialized payload.

3. **Consumer test** — produce a record with a test `KafkaTemplate`, then assert the side effect
   (order status became `PROCESSING`). Use **Awaitility**, not `Thread.sleep`:
   ```java
   await().atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(repo.findById(id).get().getStatus())
                                  .isEqualTo(OrderStatus.PROCESSING));
   ```

4. **Testcontainers variant** — one integration test using a real broker container with
   `@ServiceConnection`, so the broker address is wired into the context automatically with no
   `@DynamicPropertySource` boilerplate.

5. **Keep Kafka out of the tests that do not need it.** Either a `@Profile`/conditional on the
   listener, or a mocked publisher bean in the web slice tests. The `@WebMvcTest` from ORD-005 must
   not need a broker.

## Acceptance criteria

- [ ] `./gradlew test` passes with Docker **stopped** (embedded-broker tests only).
- [ ] The Testcontainers test is separated so it can be skipped when Docker is unavailable.
- [ ] No `Thread.sleep` anywhere in the test sources.
- [ ] A test proves the DLT behaviour from ORD-010 — a poison message ends up on the DLT.
- [ ] Running the suite twice in a row gives the same result (no cross-test offset leakage).

## Things to actually understand

- **Embedded Kafka vs. Testcontainers.** Embedded is an in-JVM broker: fast, no Docker, but not the
  real thing. Testcontainers runs the actual broker image: honest, slower, needs Docker. Use embedded
  for the bulk and Testcontainers for a small number of confidence tests.
- **Messaging tests are inherently asynchronous.** The assertion has to poll until a deadline. Every
  `Thread.sleep` in a Kafka test is either flaky or needlessly slow, usually both.
- **Test isolation.** Consumer groups remember offsets. Reusing a group id across tests means test 2
  sees test 1's messages. Use a unique group per test, or a fresh embedded broker.
- **`@ServiceConnection`** replaced the older `@DynamicPropertySource` pattern for wiring container
  connection details into the Spring context. Worth writing it both ways once to see what it saves.
- **Context caching applies here too.** Each distinct `@EmbeddedKafka` configuration creates a new
  application context. Keep the configuration identical across test classes and the suite stays fast.

## Out of scope

- Contract testing, consumer-driven contracts.
- Performance/load testing.
