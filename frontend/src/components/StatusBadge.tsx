import type { OrderStatus, PaymentStatus } from '../api/types';

export function StatusBadge({ status }: { status: OrderStatus | PaymentStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>;
}
