import { describeError } from '../api/client';
import { useSimulatePayment, usePayment } from '../api/queries';
import type { Order } from '../api/types';
import { useOrderWatch } from '../state/OrderWatchContext';
import { useSettings } from '../state/SettingsContext';
import { useTransitionLog } from '../state/TransitionLogContext';
import { money, shortId } from '../util/format';
import { StatusBadge } from './StatusBadge';

export function PaymentPanel({ order }: { order: Order }) {
  const { data: payment, isPending, error } = usePayment(order.orderId);
  const { customerIdOf } = useOrderWatch();
  const { settings } = useSettings();
  const { log } = useTransitionLog();
  const simulate = useSimulatePayment();

  if (isPending) {
    return (
      <div className="payment-box">
        <h3>Payment</h3>
        <p className="muted">Checking…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="payment-box">
        <h3>Payment</h3>
        <p className="muted">{describeError(error)}</p>
      </div>
    );
  }

  if (payment) {
    return (
      <div className="payment-box">
        <h3>Payment</h3>
        <div className="detail-grid">
          <div className="detail-cell">
            <span>Status</span>
            <strong>
              <StatusBadge status={payment.status} />
            </strong>
          </div>
          <div className="detail-cell">
            <span>Amount</span>
            <strong>{money(payment.amount, payment.currency)}</strong>
          </div>
          <div className="detail-cell">
            <span>Reference</span>
            <strong className="mono">{payment.providerReference ?? '—'}</strong>
          </div>
          {payment.failureReason && (
            <div className="detail-cell">
              <span>Declined because</span>
              <strong>{payment.failureReason}</strong>
            </div>
          )}
        </div>
      </div>
    );
  }

  const runSimulation = () => {
    const customerId = customerIdOf(order.orderId) ?? settings.customerId;
    simulate.mutate(
      { orderId: order.orderId, customerId, amount: order.total, currency: order.currency },
      {
        onSuccess: (attempt) =>
          log(
            `payment for ${shortId(order.orderId)} → ${attempt.status}` +
              (attempt.failureReason ? ` (${attempt.failureReason})` : ''),
            attempt.status === 'SUCCEEDED' ? 'ok' : 'bad',
          ),
        onError: (mutationError) =>
          log(
            `payment for ${shortId(order.orderId)} failed — ${describeError(mutationError)}`,
            'bad',
          ),
      },
    );
  };

  return (
    <div className="payment-box">
      <h3>Payment</h3>
      <p className="muted">
        No payment attempt yet — nothing has consumed this order&apos;s <code>OrderPlaced</code>{' '}
        event.
      </p>
      <button
        className="ghost small-btn"
        type="button"
        onClick={runSimulation}
        disabled={simulate.isPending}
      >
        {simulate.isPending ? 'Charging…' : 'Simulate payment'}
      </button>
      <p className="muted small" style={{ marginBottom: 0 }}>
        Calls <code>POST /api/payments/{'{orderId}'}/attempt</code> directly. Once a Kafka listener
        drives payments, this stops being needed — an attempt will already exist by the time you
        look.
      </p>
    </div>
  );
}
