import { createContext, useContext, useEffect, useMemo, useRef, type ReactNode } from 'react';
import { useOrders } from '../api/queries';
import type { OrderStatus, OrderSummary } from '../api/types';
import { useSettings } from './SettingsContext';
import { useTransitionLog } from './TransitionLogContext';
import { shortId } from '../util/format';

interface Seen {
  status: OrderStatus;
  /** When the clock started — the moment the API acknowledged the order. */
  t0: number;
  customerId: string;
}

const BAD_STATUSES: OrderStatus[] = ['FAILED', 'CANCELLED', 'REFUNDED'];

interface OrderWatchValue {
  /**
   * Start an order's clock at the moment the API returned rather than at the next
   * poll. Without it, every measured gap silently includes up to one poll interval.
   */
  markPlaced: (orderId: string, status: OrderStatus, customerId: string) => void;
  /** OrderResponse carries no customerId; cancel needs one. This is where it lives. */
  customerIdOf: (orderId: string) => string | undefined;
}

const OrderWatchContext = createContext<OrderWatchValue | null>(null);

/**
 * Owns the "what did each order look like last time" map and does the diffing.
 *
 * <p>It runs the polling query itself so observation continues while you are on the
 * place-order page. Pages calling useOrders() share this exact cache entry, so there
 * is still only one request in flight.
 */
export function OrderWatchProvider({ children }: { children: ReactNode }) {
  const { settings } = useSettings();
  const { log } = useTransitionLog();
  const { data } = useOrders(settings.polling);

  // A ref, not state: observing must not itself cause a render, and it has to survive
  // StrictMode's double-invoked effects in dev. Writing the map before logging makes a
  // repeated run a no-op instead of a duplicate line.
  const seen = useRef(new Map<string, Seen>());

  const orders: OrderSummary[] | undefined = data?.content;

  useEffect(() => {
    if (!orders) return;

    for (const order of orders) {
      const previous = seen.current.get(order.orderId);

      if (!previous) {
        seen.current.set(order.orderId, {
          status: order.status,
          t0: Date.parse(order.placedAt) || Date.now(),
          customerId: order.customerId,
        });
        continue;
      }

      if (previous.status !== order.status) {
        const elapsed = Date.now() - previous.t0;
        seen.current.set(order.orderId, { ...previous, status: order.status });

        log(
          `order ${shortId(order.orderId)}  ${previous.status} → ${order.status}`,
          BAD_STATUSES.includes(order.status) ? 'bad' : 'ok',
          elapsed,
        );
      }
    }
  }, [orders, log]);

  const value = useMemo<OrderWatchValue>(
    () => ({
      markPlaced: (orderId, status, customerId) => {
        seen.current.set(orderId, { status, t0: Date.now(), customerId });
      },
      customerIdOf: (orderId) => seen.current.get(orderId)?.customerId,
    }),
    [],
  );

  return <OrderWatchContext.Provider value={value}>{children}</OrderWatchContext.Provider>;
}

export function useOrderWatch(): OrderWatchValue {
  const context = useContext(OrderWatchContext);
  if (!context) throw new Error('useOrderWatch must be used inside OrderWatchProvider');
  return context;
}
