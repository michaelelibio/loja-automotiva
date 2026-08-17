import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminCustomerDetail, AdminCustomerFilters, AdminCustomerPage } from '@/lib/types/admin-customer';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/customers`;

export class AdminCustomerApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'AdminCustomerApiError';
  }
}

async function request<T>(url: string): Promise<T> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminCustomerApiError('Sessão não encontrada.', 401);
  const response = await fetch(url, { cache: 'no-store', headers: { Authorization: `Bearer ${token}` } });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new AdminCustomerApiError(String(object?.message || object?.error || 'Não foi possível concluir a operação.'), response.status);
  }
  return data as T;
}

export function getAdminCustomers(filters: AdminCustomerFilters): Promise<AdminCustomerPage> {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
  if (filters.search) query.set('search', filters.search);
  if (filters.hasOrders !== undefined) query.set('hasOrders', String(filters.hasOrders));
  if (filters.authProvider) query.set('authProvider', filters.authProvider);
  return request<AdminCustomerPage>(`${endpoint}?${query}`);
}

export function getAdminCustomer(id: number, orderPage: number, orderSize = 10): Promise<AdminCustomerDetail> {
  const query = new URLSearchParams({ orderPage: String(orderPage), orderSize: String(orderSize) });
  return request<AdminCustomerDetail>(`${endpoint}/${encodeURIComponent(id)}?${query}`);
}
