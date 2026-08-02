/* orders-api dev console.
 *
 * Deliberately dependency-free vanilla JS. Everything is same-origin: nginx proxies
 * /api to the Spring app, so there is no CORS configuration anywhere in the project.
 *
 * The point of this page is to make the asynchronous half of the system visible —
 * you place an order, the HTTP response says PENDING, and some milliseconds later a
 * Kafka consumer moves it somewhere else. The event log measures that gap.
 */

const API = {
  orders:      '/api/orders-service/orders',
  order:       '/api/orders-service/order',
  orderById:   (id) => `/api/orders-service/order/${encodeURIComponent(id)}`,
  cancel:      '/api/orders-service/order/cancel',
  accept:      (id) => `/api/orders-service/order/${encodeURIComponent(id)}/accept`,
  finalize:    (id) => `/api/orders-service/order/${encodeURIComponent(id)}/finalize`,
  payment:     (id) => `/api/payments/${encodeURIComponent(id)}`,
  payAttempt:  (id) => `/api/payments/${encodeURIComponent(id)}/attempt`,
  stock:       '/api/stock',
};

const POLL_MS = 1500;

/* Used when GET /api/stock does not exist yet. Mirrors PriceCatalog.java — if you
 * change the hardcoded map there before ORD-013 lands, change it here too. */
const FALLBACK_CATALOGUE = [
  { sku: 'SKU-1', description: 'Bookcase', unitPrice: 320.99 },
  { sku: 'SKU-2', description: 'Shelf',    unitPrice: 11.99  },
  { sku: 'SKU-3', description: 'Wardrobe', unitPrice: 33.99  },
];

const state = {
  catalogue: [],
  quantities: new Map(),   // sku -> qty
  known: new Map(),        // orderId -> { status, t0, placedAt }
  selectedId: null,
  polling: true,
  inFlight: false,
};

/* ── tiny helpers ─────────────────────────────────────────────── */

const $ = (id) => document.getElementById(id);

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => (
  { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
));

const shortId = (id) => String(id ?? '').slice(0, 8);

const money = (n, ccy) => {
  const value = Number(n ?? 0);
  if (!Number.isFinite(value)) return '—';
  return `${value.toFixed(2)} ${ccy ?? ''}`.trim();
};

const clockTime = (d = new Date()) =>
  d.toTimeString().slice(0, 8) + '.' + String(d.getMilliseconds()).padStart(3, '0');

function humanAge(ms) {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m`;
  return `${Math.floor(ms / 3_600_000)}h`;
}

/* An RFC 9457 ProblemDetail, or whatever else the server sent. */
class ApiError extends Error {
  constructor(status, problem) {
    const detail = problem?.detail || problem?.title || `HTTP ${status}`;
    super(detail);
    this.status = status;
    this.problem = problem;
  }
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) },
    ...options,
  });

  if (res.status === 204) return null;

  const text = await res.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); } catch { body = { detail: text.slice(0, 300) }; }
  }

  if (!res.ok) throw new ApiError(res.status, body);
  return body;
}

/* ── connection pill ──────────────────────────────────────────── */

function setApiStatus(kind, label) {
  const el = $('api-status');
  el.className = `pill pill-${kind}`;
  el.textContent = label;
}

/* ── event log ────────────────────────────────────────────────── */

function logEvent(message, kind = 'info', elapsedMs = null) {
  const list = $('event-log');
  if (list.dataset.empty !== 'false') {
    list.innerHTML = '';
    list.dataset.empty = 'false';
  }

  const li = document.createElement('li');
  li.className = `ev-${kind}`;
  li.innerHTML =
    `<span class="t">${esc(clockTime())}</span>` +
    `<span class="msg">${esc(message)}</span>` +
    (elapsedMs != null ? `<span class="elapsed">+${esc(humanAge(elapsedMs))}</span>` : '');

  list.prepend(li);
  while (list.children.length > 200) list.lastElementChild.remove();
}

/* ── catalogue ────────────────────────────────────────────────── */

async function loadCatalogue() {
  try {
    const stock = await api(API.stock);
    const items = Array.isArray(stock) ? stock : stock?.content;
    if (Array.isArray(items) && items.length) {
      state.catalogue = items.map((i) => ({
        sku: i.sku,
        description: i.description,
        unitPrice: Number(i.unitPrice),
        available: i.quantityAvailable,
      }));
      $('catalogue-source').textContent = '— live from /api/stock';
      renderCatalogue();
      return;
    }
  } catch {
    /* ORD-013 not built yet; that is the normal case today. */
  }

  state.catalogue = FALLBACK_CATALOGUE.map((i) => ({ ...i }));
  $('catalogue-source').textContent = '— hardcoded (no /api/stock yet)';
  renderCatalogue();
}

function renderCatalogue() {
  const root = $('catalogue');
  root.innerHTML = '';

  for (const item of state.catalogue) {
    const qty = state.quantities.get(item.sku) ?? 0;

    const row = document.createElement('div');
    row.className = 'cat-item' + (qty > 0 ? ' chosen' : '');
    row.innerHTML =
      `<div>
         <div class="cat-name">${esc(item.description)}</div>
         <div class="cat-sku">${esc(item.sku)}${
           item.available != null ? ` · ${esc(item.available)} available` : ''
         }</div>
       </div>
       <div class="cat-price">${esc(Number(item.unitPrice).toFixed(2))}</div>
       <div class="stepper">
         <button type="button" data-act="dec" aria-label="Remove one ${esc(item.description)}">−</button>
         <span>${qty}</span>
         <button type="button" data-act="inc" aria-label="Add one ${esc(item.description)}">+</button>
       </div>`;

    row.querySelector('[data-act="inc"]').addEventListener('click', () => bumpQty(item.sku, +1));
    row.querySelector('[data-act="dec"]').addEventListener('click', () => bumpQty(item.sku, -1));
    root.appendChild(row);
  }

  renderTotal();
}

function bumpQty(sku, delta) {
  const next = Math.max(0, (state.quantities.get(sku) ?? 0) + delta);
  if (next === 0) state.quantities.delete(sku);
  else state.quantities.set(sku, next);
  renderCatalogue();
}

function currentTotal() {
  let total = 0;
  for (const [sku, qty] of state.quantities) {
    const item = state.catalogue.find((i) => i.sku === sku);
    if (item) total += Number(item.unitPrice) * qty;
  }
  return Math.round(total * 100) / 100;
}

function renderTotal() {
  const total = currentTotal();
  const ccy = $('currency').value;
  $('order-total').textContent = state.quantities.size ? money(total, ccy) : '—';

  const hint = $('decline-hint');
  const ceiling = Number($('decline-above').value);

  if (!state.quantities.size || !Number.isFinite(ceiling)) {
    hint.classList.add('hidden');
    return;
  }

  hint.classList.remove('hidden');
  if (total > ceiling) {
    hint.className = 'decline-hint will-decline';
    hint.textContent = `Above the ${money(ceiling, ccy)} ceiling — expect PaymentFailed and a FAILED order.`;
  } else {
    hint.className = 'decline-hint will-pass';
    hint.textContent = `Within the ${money(ceiling, ccy)} ceiling — expect PaymentSucceeded and a PAID order.`;
  }
}

/* ── placing an order ─────────────────────────────────────────── */

async function placeOrder() {
  const feedback = $('place-feedback');
  const button = $('place-btn');

  const customerId = $('customer-id').value.trim();
  const currency = $('currency').value;

  const items = [...state.quantities].map(([sku, quantity]) => ({ sku, quantity }));

  if (!customerId) return showFeedback(feedback, 'error', 'Customer id is required.');
  if (!items.length) return showFeedback(feedback, 'error', 'Add at least one item.');

  button.disabled = true;
  showFeedback(feedback, '', 'Placing…');

  const startedAt = performance.now();

  try {
    const order = await api(API.order, {
      method: 'POST',
      body: JSON.stringify({ customerId, currency, items }),
    });

    const roundTrip = performance.now() - startedAt;

    // t0 is the moment the API acknowledged the order. Everything the consumers do
    // afterwards is measured from here, which is the number worth watching.
    state.known.set(order.orderId, {
      status: order.status,
      t0: Date.now(),
      placedAt: order.placedAt,
      customerId,   // OrderResponse omits it; the list endpoint and this are the only sources
    });

    logEvent(
      `order ${shortId(order.orderId)} accepted as ${order.status} (${money(order.total, order.currency)})`,
      'info',
      null,
    );

    showFeedback(feedback, 'ok', `Accepted in ${Math.round(roundTrip)}ms — watch the status change.`);
    state.selectedId = order.orderId;
    state.quantities.clear();
    renderCatalogue();
    await refreshOrders();
    await renderDetail();
  } catch (err) {
    showFeedback(feedback, 'error', describeError(err));
    logEvent(`place order failed — ${describeError(err)}`, 'bad');
  } finally {
    button.disabled = false;
  }
}

function showFeedback(el, kind, message) {
  el.className = `feedback ${kind}`;
  el.textContent = message;
}

function describeError(err) {
  if (err instanceof ApiError) {
    const p = err.problem ?? {};
    if (p.title && p.detail && p.title !== p.detail) return `${p.title}: ${p.detail}`;
    return p.detail || p.title || `HTTP ${err.status}`;
  }
  return err?.message || 'Network error — is the API running?';
}

/* ── polling the order list ───────────────────────────────────── */

async function refreshOrders() {
  if (state.inFlight) return;
  state.inFlight = true;

  try {
    const page = await api(`${API.orders}?size=25`);
    const orders = page?.content ?? [];

    setApiStatus('ok', 'api up');
    $('orders-meta').textContent =
      `${page?.page?.totalElements ?? orders.length} total · polling every ${POLL_MS}ms`;

    const changed = detectTransitions(orders);
    renderOrders(orders, changed);
  } catch (err) {
    setApiStatus('down', 'api unreachable');
    $('orders-meta').textContent = describeError(err);
  } finally {
    state.inFlight = false;
  }
}

/* Compare each order against what we saw last time and log anything that moved.
 * This is the only place status transitions are noticed. */
function detectTransitions(orders) {
  const changed = new Set();

  for (const order of orders) {
    const previous = state.known.get(order.orderId);

    if (!previous) {
      // First sight. Orders placed in another tab or by curl land here.
      state.known.set(order.orderId, {
        status: order.status,
        t0: Date.parse(order.placedAt) || Date.now(),
        placedAt: order.placedAt,
        customerId: order.customerId,
      });
      continue;
    }

    // The list endpoint is the only response carrying customerId — keep it, the
    // cancel request needs it and OrderResponse does not have it.
    previous.customerId ??= order.customerId;

    if (previous.status !== order.status) {
      const elapsed = Date.now() - previous.t0;
      const kind = ['FAILED', 'CANCELLED', 'REFUNDED'].includes(order.status) ? 'bad' : 'ok';

      logEvent(
        `order ${shortId(order.orderId)}  ${previous.status} → ${order.status}`,
        kind,
        elapsed,
      );

      previous.status = order.status;
      changed.add(order.orderId);
    }
  }

  return changed;
}

function renderOrders(orders, changed) {
  const body = $('orders-body');

  if (!orders.length) {
    body.innerHTML = '<tr><td colspan="5" class="muted center">No orders yet.</td></tr>';
    return;
  }

  body.innerHTML = '';

  for (const order of orders) {
    const tr = document.createElement('tr');
    tr.className =
      (order.orderId === state.selectedId ? 'selected ' : '') +
      (changed.has(order.orderId) ? 'changed' : '');

    const age = Date.now() - (Date.parse(order.placedAt) || Date.now());

    tr.innerHTML =
      `<td class="mono">${esc(shortId(order.orderId))}</td>
       <td>${esc(order.customerId)}</td>
       <td class="num">${esc(money(order.total, order.currency))}</td>
       <td>${statusBadge(order.status)}</td>
       <td class="num muted">${esc(humanAge(age))}</td>`;

    tr.addEventListener('click', () => {
      state.selectedId = order.orderId;
      renderOrders(orders, new Set());
      renderDetail();
    });

    body.appendChild(tr);
  }
}

function statusBadge(status) {
  return `<span class="badge badge-${esc(String(status).toLowerCase())}">${esc(status)}</span>`;
}

/* ── selected order detail ────────────────────────────────────── */

const TERMINAL = ['DELIVERED', 'CANCELLED', 'REFUNDED', 'FAILED'];

async function renderDetail() {
  const root = $('detail-body');
  const buttons = ['accept-btn', 'finalize-btn', 'cancel-btn'].map($);

  if (!state.selectedId) {
    root.innerHTML = '<p class="muted">Pick an order from the table to see its lines and payment attempt.</p>';
    buttons.forEach((b) => b.classList.add('hidden'));
    return;
  }

  let order;
  try {
    order = await api(API.orderById(state.selectedId));
  } catch (err) {
    root.innerHTML = `<p class="feedback error">${esc(describeError(err))}</p>`;
    buttons.forEach((b) => b.classList.add('hidden'));
    return;
  }

  // Mirrors the server-side state machine. The server is the authority — these only decide
  // which buttons are worth offering, and a stale page still gets a 409 rather than a wrong write.
  const terminal = TERMINAL.includes(order.status);
  $('accept-btn').classList.toggle('hidden', !['PENDING', 'PAID'].includes(order.status));
  $('finalize-btn').classList.toggle('hidden', order.status !== 'ALLOCATED');
  $('cancel-btn').classList.toggle('hidden', terminal || order.status === 'SHIPPED');

  const lines = (order.items ?? []).map((line) => `
    <tr>
      <td class="mono">${esc(line.sku)}</td>
      <td>${esc(line.description)}</td>
      <td class="num">${esc(line.quantity)}</td>
      <td class="num">${esc(Number(line.unitPrice).toFixed(2))}</td>
      <td class="num">${esc(Number(line.lineTotal).toFixed(2))}</td>
    </tr>`).join('');

  root.innerHTML = `
    <div class="detail-grid">
      <div class="detail-cell"><span>Order id</span><strong class="mono">${esc(order.orderId)}</strong></div>
      <div class="detail-cell"><span>Status</span><strong>${statusBadge(order.status)}</strong></div>
      <div class="detail-cell"><span>Total</span><strong>${esc(money(order.total, order.currency))}</strong></div>
      <div class="detail-cell"><span>Placed</span><strong>${esc(order.placedAt ? clockTime(new Date(order.placedAt)) : '—')}</strong></div>
    </div>

    ${lines ? `
      <table class="lines-table">
        <thead>
          <tr><th>SKU</th><th>Description</th><th class="num">Qty</th><th class="num">Unit</th><th class="num">Line</th></tr>
        </thead>
        <tbody>${lines}</tbody>
      </table>` : '<p class="muted small">No line items on this response.</p>'}

    <div class="payment-box" id="payment-box">
      <h3>Payment</h3>
      <p class="muted">Checking…</p>
    </div>`;

  renderPayment(order);
}

async function renderPayment(order) {
  const box = $('payment-box');
  if (!box) return;

  try {
    const payment = await api(API.payment(order.orderId));
    box.innerHTML = `
      <h3>Payment</h3>
      <div class="detail-grid">
        <div class="detail-cell"><span>Status</span><strong>${statusBadge(payment.status)}</strong></div>
        <div class="detail-cell"><span>Amount</span><strong>${esc(money(payment.amount, payment.currency))}</strong></div>
        <div class="detail-cell"><span>Reference</span><strong class="mono">${esc(payment.providerReference ?? '—')}</strong></div>
        ${payment.failureReason
          ? `<div class="detail-cell"><span>Declined because</span><strong>${esc(payment.failureReason)}</strong></div>`
          : ''}
      </div>`;
    return;
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) {
      box.innerHTML = `<h3>Payment</h3><p class="muted">${esc(describeError(err))}</p>`;
      return;
    }
  }

  // 404 — no attempt yet. Either nothing has consumed OrderPlaced, or there is no
  // consumer to. Offer the manual trigger so payments can be exercised either way.
  box.innerHTML = `
    <h3>Payment</h3>
    <p class="muted">No payment attempt yet — nothing has consumed this order's <code>OrderPlaced</code> event.</p>
    <button id="simulate-pay" class="ghost small-btn" type="button">Simulate payment</button>
    <p class="muted small" style="margin-bottom:0">
      Calls <code>POST /api/payments/{orderId}/attempt</code> directly. Once a Kafka listener drives
      payments, this button stops being needed — an attempt will already exist by the time you look.
    </p>`;

  $('simulate-pay').addEventListener('click', () => simulatePayment(order));
}

async function simulatePayment(order) {
  const button = $('simulate-pay');
  button.disabled = true;
  button.textContent = 'Charging…';

  const customerId = state.known.get(order.orderId)?.customerId || $('customer-id').value.trim();

  try {
    const payment = await api(API.payAttempt(order.orderId), {
      method: 'POST',
      body: JSON.stringify({ customerId, amount: order.total, currency: order.currency }),
    });

    logEvent(
      `payment for ${shortId(order.orderId)} → ${payment.status}` +
      (payment.failureReason ? ` (${payment.failureReason})` : ''),
      payment.status === 'SUCCEEDED' ? 'ok' : 'bad',
    );

    await renderDetail();
    await refreshOrders();
  } catch (err) {
    logEvent(`payment for ${shortId(order.orderId)} failed — ${describeError(err)}`, 'bad');
    button.disabled = false;
    button.textContent = 'Simulate payment';
  }
}

/**
 * Accept and finalize share a shape: POST, log the outcome, refresh everything.
 * The catalogue reload matters — allocation is the whole reason "available" moves.
 */
async function lifecycleAction(buttonId, url, verb) {
  if (!state.selectedId) return;

  const button = $(buttonId);
  button.disabled = true;

  try {
    const order = await api(url(state.selectedId), { method: 'POST' });
    logEvent(`order ${shortId(state.selectedId)} ${verb} → ${order.status}`, 'ok');
  } catch (err) {
    logEvent(`${verb} ${shortId(state.selectedId)} rejected — ${describeError(err)}`, 'bad');
  } finally {
    button.disabled = false;
    await Promise.all([refreshOrders(), renderDetail(), loadCatalogue()]);
  }
}

async function cancelSelected() {
  if (!state.selectedId) return;

  const button = $('cancel-btn');
  button.disabled = true;

  try {
    const customerId = state.known.get(state.selectedId)?.customerId || $('customer-id').value.trim();
    const result = await api(API.cancel, {
      method: 'POST',
      body: JSON.stringify({ orderId: state.selectedId, customerId }),
    });
    logEvent(`order ${shortId(state.selectedId)} cancelled → ${result.status ?? 'CANCELLED'}`, 'bad');
  } catch (err) {
    logEvent(`cancel ${shortId(state.selectedId)} rejected — ${describeError(err)}`, 'bad');
  } finally {
    button.disabled = false;
    // Cancelling an ALLOCATED order releases its stock, so availability moves here too.
    await Promise.all([refreshOrders(), renderDetail(), loadCatalogue()]);
  }
}

/* ── wiring ───────────────────────────────────────────────────── */

function init() {
  $('place-btn').addEventListener('click', placeOrder);
  $('cancel-btn').addEventListener('click', cancelSelected);
  $('accept-btn').addEventListener('click', () => lifecycleAction('accept-btn', API.accept, 'accepted'));
  $('finalize-btn').addEventListener('click', () => lifecycleAction('finalize-btn', API.finalize, 'finalized'));
  $('currency').addEventListener('change', renderTotal);
  $('decline-above').addEventListener('input', renderTotal);

  $('clear-log').addEventListener('click', () => {
    const list = $('event-log');
    list.innerHTML = '<li class="muted">Nothing observed yet.</li>';
    list.dataset.empty = 'true';
  });

  $('poll-enabled').addEventListener('change', (e) => {
    state.polling = e.target.checked;
    if (state.polling) refreshOrders();
    else setApiStatus('unknown', 'paused');
  });

  $('event-log').dataset.empty = 'true';

  loadCatalogue();
  refreshOrders();

  setInterval(() => {
    if (state.polling) refreshOrders();
  }, POLL_MS);
}

document.addEventListener('DOMContentLoaded', init);
