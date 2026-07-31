# ORD-006 — Actuator and custom metrics

**Teaches:** Actuator endpoints, health indicators, Micrometer counters/timers, endpoint exposure

## Problem

`spring-boot-starter-actuator` and `micrometer-registry-prometheus` are already on the classpath and
completely unused. There is no way to tell whether the app is healthy or how many orders it has
taken.

## Scope

1. Expose the actuator endpoints you want over HTTP in `application.yml`
   (`management.endpoints.web.exposure.include`). Start with `health`, `info`, `metrics`,
   `prometheus`.
2. Turn on health detail (`management.endpoint.health.show-details`) and look at what appears once
   JPA (ORD-002) and Kafka (ORD-007) are wired in — Boot contributes indicators automatically.
3. Add a custom counter: `orders.placed`, tagged by `status` and `currency`.
4. Add a custom timer around order placement so you get count, total and max latency for free.
5. Write a custom `HealthIndicator` — e.g. one that reports `DOWN` if the last order was rejected,
   just to see how the contract works.

## Acceptance criteria

- [ ] `GET /actuator/health` returns `UP` with per-component detail.
- [ ] `GET /actuator/prometheus` returns text-format metrics including `orders_placed_total`.
- [ ] Placing two orders increments the counter by two.
- [ ] The counter is tagged, so `orders_placed_total{currency="GBP"}` is distinguishable.

## Things to actually understand

- **Endpoints are enabled and exposed separately.** An endpoint can exist and still 404 because it is
  not exposed over the web. This trips people up constantly.
- **Naming conventions.** You register `orders.placed` in Micrometer; Prometheus scrapes it as
  `orders_placed_total`. The registry does the translation, which is why the metric name in your code
  never quite matches the one in the dashboard.
- **Tags are dimensions, not labels for humans.** Never tag with something unbounded like an order id
   — every distinct value creates a new time series. This is the classic cardinality mistake.
- **Counter vs. Gauge vs. Timer** — monotonic count, point-in-time value, and duration distribution.
  Picking the wrong one gives you a graph that cannot answer the question you had.

## Gotchas

- Exposing `*` also exposes `env`, `configprops` and `beans`, which leak configuration. Fine locally,
  never in production.
- Hold a reference to a `Counter` rather than calling `registry.counter(...)` inside a hot path.

## Out of scope

- Distributed tracing, log aggregation, Grafana dashboards.
