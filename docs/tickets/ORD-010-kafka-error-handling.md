# ORD-010 — Retries and a dead-letter topic

**Teaches:** `DefaultErrorHandler`, backoff, `DeadLetterPublishingRecoverer`, poison messages,
retryable vs. non-retryable failures

## Problem

Right now a listener that throws blocks its partition. With no error handler configured, the
container retries the same record and everything behind it stops. One bad message halts a partition
indefinitely.

## Scope

1. Configure a `DefaultErrorHandler` bean with a bounded backoff — e.g. `FixedBackOff` or
   `ExponentialBackOff`, 3 attempts.
2. Configure a `DeadLetterPublishingRecoverer` so exhausted records go to
   `orders.order-placed.v1.DLT`.
3. Classify exceptions:
   - **Retryable** (transient — database down, downstream timeout) → retry
   - **Non-retryable** (deserialization failure, validation failure, unknown order) → straight to
     the DLT via `addNotRetryableExceptions(...)`
4. Add a listener on the DLT that logs the payload and the failure headers, so dead letters are
   visible rather than silently accumulating.
5. Deliberately break something to prove it works — throw on any order with a specific customer id,
   and separately hand-produce malformed JSON to the topic with the console producer.

## Acceptance criteria

- [ ] A transient failure is retried 3 times with a visible delay between attempts, then lands on the
      DLT.
- [ ] A non-retryable failure goes to the DLT **immediately**, with no retries.
- [ ] Malformed JSON on the topic does not stall the partition — valid messages behind it are still
      processed.
- [ ] DLT records carry the diagnostic headers (`kafka_dlt-exception-message`,
      `kafka_dlt-original-topic`, `kafka_dlt-original-partition`, `kafka_dlt-original-offset`).
- [ ] Consumer lag on the main group returns to zero after a poison message.

## Things to actually understand

- **Blocking vs. non-blocking retry.** `DefaultErrorHandler` retries *in place*, which means the
  consumer thread sleeps and that partition makes no progress during the backoff. For long backoffs
  that is unacceptable; `@RetryableTopic` instead forwards to separate delay topics so the main
  partition keeps flowing. Know both and when each is right.
- **Why a DLT is not optional.** At-least-once delivery plus a message that can never succeed equals
  a stopped partition. The DLT is the escape valve that converts "everything is stuck" into "one
  message needs a human".
- **Not all failures deserve a retry.** Retrying a `JsonProcessingException` 3 times just wastes 3
  attempts — the bytes will not change. Classification is the whole point of the exercise.
- **`ErrorHandlingDeserializer` runs before your listener.** A deserialization failure happens during
  the poll, not in your method, so it needs this wrapper to become a handleable error rather than a
  container-level explosion.
- **DLT replay.** Think through how you would reprocess a fixed dead letter, and why "just point a
  consumer at the DLT and republish" needs care around ordering and idempotency.

## Out of scope

- Automated DLT replay tooling.
- Alerting on DLT depth (though noting how ORD-006's metrics would do it is worth a comment).
