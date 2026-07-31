# ORD-005 — A real test suite

**Teaches:** test slices vs. full context, `MockMvc`, `@MockitoBean`, plain unit tests, AssertJ

## Problem

`OrdersApplicationTests` only asserts that the context loads. Nothing verifies behaviour, so every
later ticket is a refactor with no safety net.

## Scope

Write tests at three levels and understand why each exists.

1. **Plain unit test** — `OrderServiceTest`. No Spring at all. Construct `OrderService` with `new`,
   pass a mock repository, assert on totals, status and the terminal-transition rule. Should run in
   milliseconds.
2. **Web slice test** — `OrderControllerTest` annotated `@WebMvcTest(OrderController.class)`. Loads
   only the web layer; the `OrderService` dependency is supplied as a test double. Assert status
   codes, JSON shape and the validation failures from ORD-001.
3. **Integration test** — `@SpringBootTest` with a real (in-memory) database, exercising
   place → fetch → cancel end to end.

## Acceptance criteria

- [ ] All three levels exist and `./gradlew test` is green.
- [ ] The slice test does **not** start a database or a Kafka broker.
- [ ] Validation failures are asserted at the controller level, business rules at the service level —
      not both in both places.
- [ ] At least one test asserts on JSON content, not just the status code.

## Things to actually understand

- **Why the pyramid.** The unit test tells you *what broke*, the integration test tells you *that*
  something broke. You want many of the first and few of the second.
- **What a slice actually loads.** `@WebMvcTest` registers controllers, converters and the exception
  advice — but not `@Service` or `@Repository` beans. That is why the service must be mocked, and
  why the test fails with `NoSuchBeanDefinitionException` if you forget.
- **`@SpringBootTest` starts the whole context**, including anything you add later. After ORD-008 it
  will try to reach a Kafka broker unless you deal with it — the reason ORD-011 exists.
- **Context caching.** Spring reuses a cached context across test classes with identical
  configuration. Vary the config in every class and your suite gets slow for no benefit.

## Notes on this project

- The test starter here is `spring-boot-starter-webmvc-test` (the newer name), matching
  `spring-boot-starter-webmvc` in the main source set.
- Mocking a bean inside a slice test is `@MockitoBean` in current Spring Boot — `@MockBean` is the
  older, deprecated spelling you will see in most tutorials.
- Lombok is already wired for the test source set, so `@Builder` on `OrderResponse` is available for
  building fixtures.

## Out of scope

- Kafka tests (ORD-011).
- Test coverage tooling, mutation testing.
