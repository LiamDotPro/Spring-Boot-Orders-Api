# ORD-004 — Externalise configuration

**Teaches:** `@ConfigurationProperties`, `@Value`, profiles, property precedence, `@Bean` config classes

## Problem

`application.properties` has one line in it. Every value the app needs — topic names, broker
addresses, business rules like a maximum order total — will otherwise end up hardcoded in Java.

## Scope

1. Convert `application.properties` to `application.yml` (nesting makes Kafka config far more
   readable, and you will have a lot of it after ORD-007–010).
2. Add a typed config class:

   ```
   com.liamread.orders.config.OrdersProperties   @ConfigurationProperties(prefix = "orders")
   ```

   Fields: `defaultCurrency`, `maxOrderTotal` (`BigDecimal`), `topics.orderPlaced` (nested record).
3. Bind it with `@EnableConfigurationProperties(OrdersProperties.class)` on the application class,
   and inject it where those values are needed.
4. Add profile-specific files: `application-local.yml` and `application-test.yml`, with a different
   broker address and log level in each.
5. Enforce one business rule from config — reject an order above `orders.max-order-total` with the
   409/400 machinery from ORD-003.

## Acceptance criteria

- [ ] No topic name, broker address or business threshold appears as a literal in a Java file.
- [ ] Running with `--spring.profiles.active=local` picks up `application-local.yml`.
- [ ] An environment variable overrides the YAML value without a code change
      (`ORDERS_MAXORDERTOTAL=50` — work out why that spelling is the one that works).
- [ ] A missing required property fails at **startup** with a clear message, not at first request.

## Things to actually understand

- **`@Value` vs. `@ConfigurationProperties`.** `@Value` is a single string-ish injection with no
  validation and no IDE support. `@ConfigurationProperties` gives you a typed, testable, validatable
  object. Use `@Value` only for one-offs.
- **Relaxed binding.** `orders.max-order-total`, `orders.maxOrderTotal` and `ORDERS_MAXORDERTOTAL`
  all bind to the same field. This is why the env-var override works.
- **Property precedence.** Command-line args beat env vars beat profile YAML beat `application.yml`.
  Know the order well enough to debug "why is it still using the old value".
- **Profiles are not environments.** They are just tags that switch which property files and which
  `@Profile`-annotated beans are active.
- **Validation on config:** put `@Validated` on the properties class and Jakarta constraints on its
  fields to get fail-fast startup behaviour.

## Gotchas

- `@ConfigurationProperties` on a record works, but the class must be registered — either
  `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`. Just annotating it does nothing.
- YAML is whitespace-sensitive and tabs are illegal. A silently ignored block is almost always
  indentation.

## Out of scope

- Secrets management, config server, encryption.
