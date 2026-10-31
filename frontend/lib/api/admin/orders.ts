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
