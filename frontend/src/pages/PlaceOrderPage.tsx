import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { describeError } from '../api/client';
import { usePlaceOrder, useStock } from '../api/queries';
import { useOrderWatch } from '../state/OrderWatchContext';
import { useSettings } from '../state/SettingsContext';
import { useTransitionLog } from '../state/TransitionLogContext';
import { money, shortId } from '../util/format';

const CURRENCIES = ['GBP', 'USD', 'EUR'];

export function PlaceOrderPage() {
  const { settings, update } = useSettings();
  const { data: stock, isPending, error } = useStock();
  const { markPlaced } = useOrderWatch();
  const { log } = useTransitionLog();
  const placeOrder = usePlaceOrder();
  const navigate = useNavigate();

  const [quantities, setQuantities] = useState<Record<string, number>>({});

  const bump = (sku: string, delta: number) =>
    setQuantities((previous) => {
      const next = Math.max(0, (previous[sku] ?? 0) + delta);
      const { [sku]: _removed, ...rest } = previous;
      return next === 0 ? rest : { ...rest, [sku]: next };
    });

  const chosen = Object.entries(quantities);
  const total =
    Math.round(
      chosen.reduce((sum, [sku, quantity]) => {
        const item = stock?.find((candidate) => candidate.sku === sku);
        return sum + (item ? item.unitPrice * quantity : 0);
      }, 0) * 100,
    ) / 100;

  // Ordering more than is on the shelf is allowed by design — placing an order prices
  // it and reserves nothing. The shortfall only bites at accept, with a 409.
  const shortfalls = chosen
    .map(([sku, quantity]) => {
      const item = stock?.find((candidate) => candidate.sku === sku);
      return item && quantity > item.quantityAvailable
        ? { sku, quantity, available: item.quantityAvailable }
        : null;
    })
    .filter((entry): entry is { sku: string; quantity: number; available: number } => entry !== null);

  const willDecline = chosen.length > 0 && total > settings.declineAbove;

  const submit = () => {
    const items = chosen.map(([sku, quantity]) => ({ sku, quantity }));
    placeOrder.mutate(
      { customerId: settings.customerId.trim(), currency: settings.currency, items },
      {
        onSuccess: (order) => {
          markPlaced(order.orderId, order.status, settings.customerId.trim());
          log(
            `order ${shortId(order.orderId)} accepted as ${order.status} (${money(order.total, order.currency)})`,
          );
          setQuantities({});
          navigate(`/orders/${order.orderId}`);
        },
        onError: (placeError) => log(`place order failed — ${describeError(placeError)}`, 'bad'),
      },
    );
  };

  return (
    <div className="page page-narrow">
      <section className="card">
        <h2>Place an order</h2>

        <div className="field-row">
          <label className="field">
            <span>Customer id</span>
            <input
              type="text"
              value={settings.customerId}
              autoComplete="off"
              onChange={(event) => update({ customerId: event.target.value })}
            />
          </label>
          <label className="field field-narrow">
            <span>Currency</span>
            <select
              value={settings.currency}
              onChange={(event) => update({ currency: event.target.value })}
            >
              {CURRENCIES.map((currency) => (
                <option key={currency} value={currency}>
                  {currency}
                </option>
              ))}
            </select>
          </label>
        </div>

        <h3 className="section-label">Catalogue</h3>

        {isPending && <p className="muted">Loading catalogue…</p>}
        {error && <p className="feedback error">{describeError(error)}</p>}

        <div className="catalogue">
          {stock?.map((item) => {
            const quantity = quantities[item.sku] ?? 0;
            const out = item.quantityAvailable <= 0;
            return (
              <div key={item.sku} className={`cat-item${quantity > 0 ? ' chosen' : ''}`}>
                <div>
                  <div className="cat-name">{item.description}</div>
                  <div className="cat-sku">
                    {item.sku} ·{' '}
                    <span className={out ? 'out-of-stock' : undefined}>
                      {out ? 'out of stock' : `${item.quantityAvailable} available`}
                    </span>
                  </div>
                </div>
                <div className="cat-price">{item.unitPrice.toFixed(2)}</div>
                <div className="stepper">
                  <button type="button" onClick={() => bump(item.sku, -1)} aria-label={`Remove one ${item.description}`}>
                    −
                  </button>
                  <span>{quantity}</span>
                  <button type="button" onClick={() => bump(item.sku, 1)} aria-label={`Add one ${item.description}`}>
                    +
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        <div className="totals">
          <div className="totals-line">
            <span>Order total</span>
            <strong>{chosen.length ? money(total, settings.currency) : '—'}</strong>
          </div>

          {shortfalls.length > 0 && (
            <div className="decline-hint will-warn">
              Ordering more than is on the shelf —{' '}
              {shortfalls
                .map((entry) => `${entry.sku} ×${entry.quantity} vs ${entry.available} available`)
                .join(', ')}
              . This will still be accepted: placing an order reserves nothing. It is{' '}
              <strong>Accept</strong> that will return 409.
            </div>
          )}

          {chosen.length > 0 && (
            <div className={`decline-hint ${willDecline ? 'will-decline' : 'will-pass'}`}>
              {willDecline
                ? `Above the ${money(settings.declineAbove, settings.currency)} ceiling — expect PaymentFailed and a FAILED order.`
                : `Within the ${money(settings.declineAbove, settings.currency)} ceiling — expect PaymentSucceeded and a PAID order.`}
            </div>
          )}
        </div>

        <details className="sim">
          <summary>Payment simulation</summary>
          <p className="muted small">
            Mirror of <code>payments.auto-decline-above</code>. Set the same number here and the
            console predicts the outcome before you place the order.
          </p>
          <label className="field field-narrow">
            <span>Decline above</span>
            <input
              type="number"
              min={0}
              step="0.01"
              value={settings.declineAbove}
              onChange={(event) => update({ declineAbove: Number(event.target.value) })}
            />
          </label>
        </details>

        <button
          className="primary"
          type="button"
          onClick={submit}
          disabled={placeOrder.isPending || chosen.length === 0 || !settings.customerId.trim()}
        >
          {placeOrder.isPending ? 'Placing…' : 'Place order'}
        </button>

        {placeOrder.isError && (
          <div className="feedback error">{describeError(placeOrder.error)}</div>
        )}
      </section>
    </div>
  );
}
