import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { apiFetch, isNotFound } from './client';
import type {
  Order,
  OrderSummary,
  PagedModel,
  PaymentAttempt,
  PlaceOrderRequest,
  StockItem,
} from './types';

const ORDERS = '/api/orders-service';

export const keys = {
  stock: ['stock'] as const,
  orders: ['orders'] as const,
  order: (id: string) => ['order', id] as const,
  payment: (id: string) => ['payment', id] as const,
};

export const POLL_MS = 1500;

/* ── reads ─────────────────────────────────────────────────────── */

export function useStock(): UseQueryResult<StockItem[]> {
  return useQuery({
    queryKey: keys.stock,
    queryFn: () => apiFetch<StockItem[]>('/api/stock'),
    staleTime: 1000,
  });
}

/**
 * The polling query. Mounted once in the layout so both pages read the same cache
 * entry and only one request is ever in flight, and so the transition watcher keeps
 * observing while you are on the place-order page.
 */
export function useOrders(polling: boolean): UseQueryResult<PagedModel<OrderSummary>> {
  return useQuery({
    queryKey: keys.orders,
    queryFn: () => apiFetch<PagedModel<OrderSummary>>(`${ORDERS}/orders?size=25`),
    refetchInterval: polling ? POLL_MS : false,
    refetchIntervalInBackground: false,
    // A dev console should show that the API is down, not a stale list from a minute ago.
    retry: false,
  });
}

export function useOrder(orderId: string | undefined): UseQueryResult<Order> {
  return useQuery({
    queryKey: keys.order(orderId ?? ''),
    queryFn: () => apiFetch<Order>(`${ORDERS}/order/${orderId}`),
    enabled: Boolean(orderId),
    retry: false,
  });
}

/**
 * A 404 here is the normal answer between an order being placed and its event being
 * consumed, so it resolves to null rather than erroring — "not yet", not "broken".
 */
export function usePayment(orderId: string | undefined): UseQueryResult<PaymentAttempt | null> {
  return useQuery({
    queryKey: keys.payment(orderId ?? ''),
    queryFn: async () => {
      try {
        return await apiFetch<PaymentAttempt>(`/api/payments/${orderId}`);
      } catch (error) {
        if (isNotFound(error)) return null;
        throw error;
      }
    },
    enabled: Boolean(orderId),
    retry: false,
  });
}

/* ── writes ────────────────────────────────────────────────────── */

/** Everything an order action can move: the list, that order, and stock levels. */
function useInvalidateOrder() {
  const queryClient = useQueryClient();
  return (orderId: string) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.orders }),
      queryClient.invalidateQueries({ queryKey: keys.order(orderId) }),
      queryClient.invalidateQueries({ queryKey: keys.payment(orderId) }),
      queryClient.invalidateQueries({ queryKey: keys.stock }),
    ]);
}

export function usePlaceOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: PlaceOrderRequest) =>
      apiFetch<Order>(`${ORDERS}/order`, { method: 'POST', body: JSON.stringify(request) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.orders }),
  });
}

export function useAcceptOrder() {
  const invalidate = useInvalidateOrder();
  return useMutation({
    mutationFn: (orderId: string) =>
      apiFetch<Order>(`${ORDERS}/order/${orderId}/accept`, { method: 'POST' }),
    onSettled: (_data, _error, orderId) => invalidate(orderId),
  });
}

export function useFinalizeOrder() {
  const invalidate = useInvalidateOrder();
  return useMutation({
    mutationFn: (orderId: string) =>
      apiFetch<Order>(`${ORDERS}/order/${orderId}/finalize`, { method: 'POST' }),
    onSettled: (_data, _error, orderId) => invalidate(orderId),
  });
}

export function useCancelOrder() {
  const invalidate = useInvalidateOrder();
  return useMutation({
    mutationFn: ({ orderId, customerId }: { orderId: string; customerId: string }) =>
      apiFetch<{ status: string }>(`${ORDERS}/order/cancel`, {
        method: 'POST',
        body: JSON.stringify({ orderId, customerId }),
      }),
    onSettled: (_data, _error, variables) => invalidate(variables.orderId),
  });
}

export function useSimulatePayment() {
  const invalidate = useInvalidateOrder();
  return useMutation({
    mutationFn: ({
      orderId,
      customerId,
      amount,
      currency,
    }: {
      orderId: string;
      customerId: string;
      amount: number;
      currency: string;
    }) =>
      apiFetch<PaymentAttempt>(`/api/payments/${orderId}/attempt`, {
        method: 'POST',
        body: JSON.stringify({ customerId, amount, currency }),
      }),
    onSettled: (_data, _error, variables) => invalidate(variables.orderId),
  });
}
