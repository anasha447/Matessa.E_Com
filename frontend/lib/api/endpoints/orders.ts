// Orders & checkout (API.md §4.10). Auth + owner; staff order management.
import { http } from '../http';
import type { OrderStatus, Page, PageParams } from '@/lib/types/common';
import type {
  CheckoutRequest,
  GuestCheckoutRequest,
  OrderResponse,
  OrderStatusHistoryResponse,
  OrderSummaryResponse,
  UpdateOrderStatusRequest,
  UpdateShippingRequest,
} from '@/lib/types/order';

export type OrderAdminListParams = PageParams & {
  status?: OrderStatus;
};

export const ordersApi = {
  /** Place an order. Throws ApiError 409 if a line went out of stock. An optional
   *  idempotencyKey makes a retried submit return the same order instead of a duplicate. */
  checkout: (body: CheckoutRequest, idempotencyKey?: string) =>
    http.post<OrderResponse>('/api/orders', body,
      idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined),

  /** Guest checkout — no account. Public. Returns the order (incl. order number). */
  guestCheckout: (body: GuestCheckoutRequest, idempotencyKey?: string) =>
    http.post<OrderResponse>('/api/orders/guest', body, {
      auth: false,
      ...(idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : {}),
    }),

  /** Public guest-order tracking by order number + the email it was placed with. */
  track: (orderNumber: string, email: string) =>
    http.get<OrderResponse>('/api/orders/track', {
      query: { number: orderNumber, email },
      auth: false,
    }),

  listMine: (params?: PageParams) =>
    http.get<Page<OrderSummaryResponse>>('/api/orders', { query: params }),

  get: (id: number) => http.get<OrderResponse>(`/api/orders/${id}`),

  cancel: (id: number) => http.post<OrderResponse>(`/api/orders/${id}/cancel`),

  history: (id: number) =>
    http.get<OrderStatusHistoryResponse[]>(`/api/orders/${id}/history`),

  // ---- staff ----
  listAll: (params?: OrderAdminListParams) =>
    http.get<Page<OrderSummaryResponse>>('/api/orders/admin', { query: params }),

  updateStatus: (id: number, body: UpdateOrderStatusRequest) =>
    http.put<OrderResponse>(`/api/orders/${id}/status`, body),

  updateShipping: (id: number, body: UpdateShippingRequest) =>
    http.put<OrderResponse>(`/api/orders/${id}/shipping`, body),
};
