# Dev console

A one-page browser client for `orders-api`. Place an order, watch a Kafka consumer change its
status a moment later, and see how long that took.

Plain HTML/CSS/JS served by nginx — no npm, no build step, no external requests. nginx also
proxies `/api` to the Spring app, which means the browser only ever talks to one origin and the
Java side needs **no CORS configuration**.

## Run it

The app is expected to be running on your machine (IDE or `./gradlew bootRun`), which is the
default this compose setup assumes:

```bash
docker compose --profile web up -d --build
# http://localhost:3000
```

If you are running the API in Docker too, point the proxy at the container instead:

```bash
API_UPSTREAM=api:8080 docker compose --profile app --profile web up -d --build
```

On Windows/macOS, PowerShell/CMD do not support the inline `VAR=x cmd` form — use
`$env:API_UPSTREAM = "api:8080"` first, or put `API_UPSTREAM=api:8080` in a `.env` file next to
`compose.yaml`.

## What it does

| Panel | What it is for |
|---|---|
| **Place an order** | Customer, currency, quantities from the catalogue. Shows the running total and predicts the payment outcome against the ORD-014 decline ceiling. |
| **Orders** | Polls `GET /api/orders-service/orders` every 1.5s. Rows flash when a status changes. |
| **Selected order** | Line items, plus the payment attempt from `GET /api/payments/{orderId}`. When no attempt exists, a **Simulate payment** button calls `POST /api/payments/{orderId}/attempt` directly — useful until a Kafka listener drives payments, after which an attempt will already be there. Cancel button. |
| **Observed transitions** | Every status change this browser saw, with the elapsed time since the order was accepted. |

That last panel is the reason the page exists. `POST /order` returns `202` with `PENDING`; some
milliseconds later a consumer moves it. The number next to each transition is your eventual
consistency window, measured rather than imagined.

## What it assumes

- `POST /api/orders-service/order`, `GET .../orders`, `GET .../order/{id}`,
  `POST .../order/cancel` — all present today.
- `GET /api/stock` (ORD-013) — used for the catalogue if it exists, otherwise the page falls back
  to a hardcoded copy of `PriceCatalog.java`. **If you change the SKUs or prices there before
  ORD-013 lands, update `FALLBACK_CATALOGUE` in `html/app.js` too.**
- `GET /api/payments/{orderId}` (ORD-014) — a 404 is treated as "not built yet, or the event has
  not been consumed", which is correct in both cases.

## Files

```
frontend/
  Dockerfile                     nginx + static files, no build stage
  nginx/default.conf.template    envsubst'd at container start; API_UPSTREAM is the only variable
  html/index.html                structure
  html/styles.css                light/dark via prefers-color-scheme
  html/app.js                    all behaviour; no dependencies
```

## Troubleshooting

**"api unreachable" in the top right.** The proxy cannot reach the upstream. Check the app is
actually listening on 8080, then `docker compose logs web`.

**502 with `host.docker.internal could not be resolved`.** The nginx config resolves the upstream
at request time via Docker's embedded DNS (127.0.0.11) so the container starts whether or not the
API is up. If your Docker version does not serve `host.docker.internal` from that resolver, set
`API_UPSTREAM` to the gateway IP directly, or run the API in compose with `API_UPSTREAM=api:8080`.

**Changes to the HTML/JS not showing.** The files are baked into the image — rebuild with
`docker compose --profile web up -d --build`. For a faster edit loop, bind-mount instead:
`volumes: ["./frontend/html:/usr/share/nginx/html:ro"]`.
