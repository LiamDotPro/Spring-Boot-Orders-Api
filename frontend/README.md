# Dev console

A React browser client for `orders-api`. Place an order, drive it through its lifecycle, and watch
Kafka consumers change its status a moment later.

React 19 + TypeScript + TanStack Query + React Router, built by Vite and served by nginx. nginx also
proxies `/api` to the Spring app, so the browser only ever talks to one origin and the Java side
needs **no CORS configuration**.

## Run it

### Against the app on your machine (IDE / `./gradlew bootRun`)

```bash
cd frontend
npm install
npm run dev          # http://localhost:3000, hot reload, proxies /api to localhost:8080
```

Point the dev proxy somewhere else with `VITE_API_TARGET=http://localhost:9090 npm run dev`.

### In Docker

```bash
docker compose --profile web up -d --build       # http://localhost:3000
```

That default assumes the API is on your host. If it is running in compose too:

```bash
API_UPSTREAM=api:8080 docker compose --profile app --profile web up -d --build
```

PowerShell has no inline `VAR=x cmd` form — use `$env:API_UPSTREAM = "api:8080"` first, or put
`API_UPSTREAM=api:8080` in a `.env` next to `compose.yaml`.

## The two pages

| Route | What it is |
|---|---|
| `/` | **Place order.** Customer, currency, quantities. Shows the running total and predicts the payment outcome against the decline ceiling. |
| `/orders`, `/orders/:orderId` | **Orders panel.** Live table, selected order with lines and payment, stock levels, and the transition log. |

The selected order lives in the URL, so a specific order is linkable and survives a reload.

### Ordering more than is in stock is allowed

Deliberately. Placing an order prices it and reserves nothing — stock only moves at **Accept**. The
catalogue shows availability and warns when you exceed it, but never blocks you, because the API
does not. The 409 arrives at accept time, which is where the check actually lives.

## How the data layer is wired

- **One polling query.** `useOrders()` runs in `OrderWatchProvider`, above the router. Both pages
  call the same hook and read the same cache entry, so there is exactly one request in flight and
  observation continues while you are on the place-order page.
- **Transitions are diffed in a ref**, not state — observing must not itself cause a render, and it
  has to survive StrictMode's double-invoked effects in dev.
- **404 from `/api/payments/{id}` resolves to `null`, not an error.** Between an order being placed
  and its event being consumed, "no payment yet" is the correct answer, not a fault.
- **Mutations invalidate orders, that order, its payment and stock.** Accepting changes all four.

## What it assumes

- `POST /api/orders-service/order`, `GET .../orders`, `GET .../order/{id}`,
  `POST .../order/{id}/accept`, `POST .../order/{id}/finalize`, `POST .../order/cancel`
- `GET /api/stock` (ORD-013)
- `GET /api/payments/{orderId}` and `POST /api/payments/{orderId}/attempt` (ORD-014)

`src/api/types.ts` mirrors the Java records by hand. When a DTO changes on that side, change it
there — nothing generates or checks it for you.

## Known advisory

`react-router-dom` 7.18.2 carries one open high advisory (GHSA-qwww-vcr4-c8h2, CSRF bypass in **RSC
mode**). There is no fixed release on the 7.x line and no 8.x published. It does not apply here —
this app uses `BrowserRouter` with plain `<Routes>`, no RSC, no server actions, no data-router
actions — and the console is a localhost dev tool. Earlier 7.x releases are strictly worse: 7.11
carries fourteen advisories that 7.18 already fixes. Re-check when a fix ships.

## Files

```
frontend/
  Dockerfile                   node build stage → nginx runtime
  nginx/default.conf.template  envsubst'd at start; SPA fallback + /api proxy
  vite.config.ts               dev server + /api proxy
  src/api/                     types, fetch client, query & mutation hooks
  src/state/                   settings, transition log, order watcher (all above the router)
  src/components/              layout, table, detail, payment, log
  src/pages/                   PlaceOrderPage, OrdersPage
```

## Troubleshooting

**"api unreachable".** The proxy cannot reach the upstream. Check the app is listening on 8080,
then `docker compose logs web`. Note the console reports whatever answers on that port — it cannot
tell your process from a stray one. `netstat -ano | findstr :8080` gives you the PID.

**404 on reloading `/orders`.** The SPA fallback is missing — that is `try_files $uri $uri/
/index.html` in the nginx template. `npm run dev` handles it automatically.

**Changes not showing in Docker.** Assets are baked into the image; rebuild with
`docker compose --profile web up -d --build`. Use `npm run dev` for a fast loop instead.
