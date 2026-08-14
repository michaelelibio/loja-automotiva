import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminOrder, AdminOrderPage, AdminOrderStatus } from '@/lib/types/admin-order';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/orders`;

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
