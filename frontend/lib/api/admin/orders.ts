import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminAliExpressOrder, AdminOrder, AdminOrderFulfillment, AdminOrderPage, AdminOrderStatus } from '@/lib/types/admin-order';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/orders`;

export function getAssistedFreightSuggestions(id: number): Promise<import('@/lib/admin/assisted-freight').AssistedFreightPreview> {
  return request(`${endpoint}/${id}/quote/suggestions`, { method: 'POST' });
}

export class AdminOrderApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'AdminOrderApiError'; }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminOrderApiError('Sessão não encontrada.', 401);
  const response = await fetch(url, { ...init, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, ...init?.headers } });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new AdminOrderApiError(String(object?.message || object?.error || 'Não foi possível concluir a operação.'), response.status);
  }
  return data as T;
}

export function getAdminOrders(page: number, size: number, status?: AdminOrderStatus): Promise<AdminOrderPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  return request<AdminOrderPage>(`${endpoint}?${query}`);
}

export function getAdminOrder(id: number): Promise<AdminOrder> {
  return request<AdminOrder>(`${endpoint}/${encodeURIComponent(id)}`);
}

export function updateAdminOrderStatus(id: number, status: 'PROCESSING' | 'SHIPPED' | 'DELIVERED'): Promise<AdminOrder> {
  return request<AdminOrder>(`${endpoint}/${encodeURIComponent(id)}/status`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status }),
  });
}

export function getAdminOrderFulfillment(id: number): Promise<AdminOrderFulfillment> {
  return request<AdminOrderFulfillment>(`${endpoint}/${encodeURIComponent(id)}/fulfillment`);
}

export function retryAdminOrderFulfillment(id: number): Promise<AdminOrderFulfillment> {
  return request<AdminOrderFulfillment>(`${endpoint}/${encodeURIComponent(id)}/fulfillment/retry`, {
    method: 'POST',
  });
}

async function aliExpressOrderRequest(id: number, send: boolean, sync = false): Promise<AdminAliExpressOrder> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminOrderApiError('Sua sessão expirou. Entre novamente.', 401);
  let response: Response;
  try {
    response = await fetch(`${endpoint}/${encodeURIComponent(id)}/aliexpress${sync ? '/sync' : send ? '/send' : ''}`, {
      method: send || sync ? 'POST' : 'GET', cache: 'no-store', headers: { Authorization: `Bearer ${token}` },
    });
  } catch { throw new AdminOrderApiError('Não foi possível confirmar o resultado. Atualize o pedido antes de continuar.', 0); }
  if (!response.ok) throw new AdminOrderApiError(response.status === 409
    ? 'Envio bloqueado. Atualize o pedido e confira os pré-requisitos ou a necessidade de reconciliação.'
    : 'Não foi possível consultar o envio ao AliExpress. Atualize o pedido.', response.status);
  try {
    const data: AdminAliExpressOrder = await response.json();
    if (data.orderId !== id || typeof data.canSend !== 'boolean' || typeof data.paymentConfirmed !== 'boolean'
      || !['NOT_REQUIRED', 'PENDING', 'PROCESSING', 'CREATED', 'FAILED', 'VERIFICATION_REQUIRED'].includes(data.status)) throw new Error();
    return data;
  } catch { throw new AdminOrderApiError('Resposta não confirmada. Atualize o pedido antes de continuar.', 0); }
}

export function getAdminAliExpressOrder(id: number) { return aliExpressOrderRequest(id, false); }
export function sendAdminAliExpressOrder(id: number) { return aliExpressOrderRequest(id, true); }
export function syncAdminAliExpressOrder(id: number) { return aliExpressOrderRequest(id, false, true); }

export function assistedQuoteAction(id: number, action: string, data?: unknown): Promise<unknown> {
  return request(endpoint+'/'+id+'/quote'+(action ? '/'+action : ''), {method:'POST',headers:{'Content-Type':'application/json'},...(data ? {body:JSON.stringify(data)} : {})});
}
export function getFulfillmentRecipient(id:number): Promise<{cpf:string;birthDate:string;phone:string}> {
  return request(endpoint+'/'+id+'/recipient-for-fulfillment');
}

export interface AdminAliExpressTest {
  orderId: number; testOrder: boolean; simulatedPaymentAt: string | null; simulatedPaymentBy: string | null;
  simulatedSupplierAt: string | null; simulatedSupplierId: string | null; simulatedSupplierBy: string | null;
  canCreateReal: boolean; realBlockers: string[];
  fixture: { id: string; digest: string; authorizationReference: string; productId: string; skuId: string;
    quantity: number; deliveryService: string | null; currency: string; productCost: number | null; shippingCost: number | null;
    taxCost: number | null; totalCost: number | null; city: string } | null;
  providerError?: { code: string; subCode: string | null; message: string; httpStatus: number | null } | null;
  realStatus: string | null; idempotencyKey: string | null; submittedAt: string | null; createdAt: string | null;
  submittedBy: string | null; supplierOrderIds: string[]; submittedFixtureDigest: string | null;
  supplierSync: { checkedAt: string | null; inProgress: boolean; parts: { orderId: string; state: string; orderStatus: string | null; verified: boolean; checkedAt: string }[] } | null;
}

export function getAdminAliExpressTest(id: number): Promise<AdminAliExpressTest> {
  return request(`${endpoint}/${id}/aliexpress-tests`);
}
export interface AdminAliExpressReview {
  orderId: number; digest: string; authorizationReference: string; payload: Record<string, unknown>;
  preflightId: string | null; buyerAccount: string | null; buyerState: 'KNOWN' | 'UNKNOWN'; warnings: string[]; productCostSource: string; inGarageProductTotal: number; checkedAt: string | null; validUntil: string | null;
  costs: NonNullable<AdminAliExpressTest['fixture']>;
}
export function preflightAdminAliExpressTest(id: number): Promise<AdminAliExpressReview> {
  return request(`${endpoint}/${id}/aliexpress-tests/preflight`, { method: 'POST' });
}
export function getAdminAliExpressTestPayload(id: number): Promise<AdminAliExpressReview> {
  return request(`${endpoint}/${id}/aliexpress-tests/payload-review`);
}
export function runAdminAliExpressTest(id: number, action: 'simulate-payment' | 'simulate-supplier' | 'create-real' | 'sync', confirmation?: {
  phrase: string; idempotencyKey: string; fixtureDigest: string; preflightId: string; acceptKnownAndUnknownCosts: boolean;
}): Promise<AdminAliExpressTest> {
  return request(`${endpoint}/${id}/aliexpress-tests/${action}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, ...(confirmation ? { body: JSON.stringify(confirmation) } : {}) });
}
