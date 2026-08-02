/** Mirrors the Java DTOs. When a record changes on that side, change it here. */

export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'ALLOCATED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'FAILED';

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';

/** Statuses the order will never leave. Mirrors OrderStatus.isTerminal(). */
export const TERMINAL_STATUSES: OrderStatus[] = ['DELIVERED', 'CANCELLED', 'REFUNDED', 'FAILED'];

export interface StockItem {
  sku: string;
  description: string;
  unitPrice: number;
  quantityOnHand: number;
  quantityAllocated: number;
  quantityAvailable: number;
}

export interface OrderLine {
  sku: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** GET /api/orders-service/order/{id} — note it carries no customerId. */
export interface Order {
  orderId: string;
  status: OrderStatus;
  total: number;
  placedAt: string;
  items: OrderLine[];
  currency: string;
}

/** GET /api/orders-service/orders — flat, no line items, and the only source of customerId. */
export interface OrderSummary {
  orderId: string;
  customerId: string;
  status: OrderStatus;
  total: number;
  currency: string;
  placedAt: string;
}

export interface PaymentAttempt {
  paymentId: string;
  orderId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  providerReference: string | null;
  failureReason: string | null;
  requestedAt: string;
  completedAt: string | null;
}

/** Spring Data's PagedModel serialisation. */
export interface PagedModel<T> {
  content: T[];
  page?: { size: number; number: number; totalElements: number; totalPages: number };
}

/** RFC 9457, plus the extension members GlobalExceptionHandler sets. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  sku?: string;
  requested?: number;
  available?: number;
  currentStatus?: OrderStatus;
  requestedStatus?: OrderStatus;
}

export interface PlaceOrderRequest {
  customerId: string;
  currency: string;
  items: { sku: string; quantity: number }[];
}
