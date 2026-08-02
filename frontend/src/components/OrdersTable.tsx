import type { OrderSummary } from '../api/types';
import { ageOf, humanDuration, money, shortId } from '../util/format';
import { StatusBadge } from './StatusBadge';

interface Props {
  orders: OrderSummary[];
  selectedId?: string;
  onSelect: (orderId: string) => void;
}

export function OrdersTable({ orders, selectedId, onSelect }: Props) {
  if (orders.length === 0) {
    return <p className="muted center">No orders yet.</p>;
  }

  return (
    <div className="table-scroll">
      <table className="orders-table">
        <thead>
          <tr>
            <th>Order</th>
            <th>Customer</th>
            <th className="num">Total</th>
            <th>Status</th>
            <th className="num">Age</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr
              key={order.orderId}
              className={order.orderId === selectedId ? 'selected' : undefined}
              onClick={() => onSelect(order.orderId)}
            >
              <td className="mono">{shortId(order.orderId)}</td>
              <td>{order.customerId}</td>
              <td className="num">{money(order.total, order.currency)}</td>
              <td>
                <StatusBadge status={order.status} />
              </td>
              <td className="num muted">{humanDuration(ageOf(order.placedAt))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
