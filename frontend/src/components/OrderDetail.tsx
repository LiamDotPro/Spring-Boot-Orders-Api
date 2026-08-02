import { describeError } from '../api/client';
import { useAcceptOrder, useCancelOrder, useFinalizeOrder, useOrder } from '../api/queries';
import { TERMINAL_STATUSES } from '../api/types';
import { useOrderWatch } from '../state/OrderWatchContext';
import { useSettings } from '../state/SettingsContext';
import { useTransitionLog } from '../state/TransitionLogContext';
import { clockTime, money, shortId } from '../util/format';
import { PaymentPanel } from './PaymentPanel';
import { StatusBadge } from './StatusBadge';

export function OrderDetail({ orderId }: { orderId: string }) {
  const { data: order, isPending, error } = useOrder(orderId);
  const { customerIdOf } = useOrderWatch();
  const { settings } = useSettings();
  const { log } = useTransitionLog();

  const accept = useAcceptOrder();
  const finalize = useFinalizeOrder();
  const cancel = useCancelOrder();

  if (isPending) return <p className="muted">Loading order…</p>;
  if (error) return <p className="feedback error">{describeError(error)}</p>;

  const terminal = TERMINAL_STATUSES.includes(order.status);

  // Mirrors the server-side state machine, but the server stays the authority — a
  // stale page gets a 409, not a wrong write.
  const canAccept = order.status === 'PENDING' || order.status === 'PAID';
  const canFinalize = order.status === 'ALLOCATED';
  const canCancel = !terminal && order.status !== 'SHIPPED';

  const busy = accept.isPending || finalize.isPending || cancel.isPending;

  const run = (verb: string, action: () => Promise<{ status?: string }>) => {
    action()
      .then((result) => log(`order ${shortId(orderId)} ${verb} → ${result.status ?? '?'}`, 'ok'))
      .catch((actionError) =>
        log(`${verb} ${shortId(orderId)} rejected — ${describeError(actionError)}`, 'bad'),
      );
  };

  return (
    <>
      <div className="detail-actions">
        {canAccept && (
          <button
            className="ok-btn small-btn"
            type="button"
            disabled={busy}
            onClick={() => run('accepted', () => accept.mutateAsync(orderId))}
          >
            Accept
          </button>
        )}
        {canFinalize && (
          <button
            className="ok-btn small-btn"
            type="button"
            disabled={busy}
            onClick={() => run('finalized', () => finalize.mutateAsync(orderId))}
          >
            Finalize
          </button>
        )}
        {canCancel && (
          <button
            className="danger small-btn"
            type="button"
            disabled={busy}
            onClick={() =>
              run('cancelled', () =>
                cancel.mutateAsync({
                  orderId,
                  customerId: customerIdOf(orderId) ?? settings.customerId,
                }),
              )
            }
          >
            Cancel order
          </button>
        )}
      </div>

      <div className="detail-grid">
        <div className="detail-cell">
          <span>Order id</span>
          <strong className="mono">{order.orderId}</strong>
        </div>
        <div className="detail-cell">
          <span>Status</span>
          <strong>
            <StatusBadge status={order.status} />
          </strong>
        </div>
        <div className="detail-cell">
          <span>Total</span>
          <strong>{money(order.total, order.currency)}</strong>
        </div>
        <div className="detail-cell">
          <span>Placed</span>
          <strong>{order.placedAt ? clockTime(new Date(order.placedAt)) : '—'}</strong>
        </div>
      </div>

      {order.items.length > 0 ? (
        <table className="lines-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>Description</th>
              <th className="num">Qty</th>
              <th className="num">Unit</th>
              <th className="num">Line</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((line) => (
              <tr key={line.sku}>
                <td className="mono">{line.sku}</td>
                <td>{line.description}</td>
                <td className="num">{line.quantity}</td>
                <td className="num">{line.unitPrice.toFixed(2)}</td>
                <td className="num">{line.lineTotal.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="muted small">No line items on this response.</p>
      )}

      <PaymentPanel order={order} />
    </>
  );
}
