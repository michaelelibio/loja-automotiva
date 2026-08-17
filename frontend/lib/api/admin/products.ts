import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminProduct, AdminProductFilters, AdminProductPage, AdminProductPayload } from '@/lib/types/admin-product';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/products`;

export class AdminProductApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly fields: Record<string, string> = {}) {
    super(message);
    this.name = 'AdminProductApiError';
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminProductApiError('Sessão não encontrada.', 401);
  const response = await fetch(url, {
    ...init,
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}`, ...init?.headers },
  });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? object.fields as Record<string, string> : {};
    throw new AdminProductApiError(String(object?.message || object?.error || 'Não foi possível concluir a operação.'), response.status, fields);
  }
  return data as T;
}

export function getAdminProducts(filters: AdminProductFilters): Promise<AdminProductPage> {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
  if (filters.search) query.set('search', filters.search);
  if (filters.active !== undefined) query.set('active', String(filters.active));
  if (filters.category) query.set('category', filters.category);
  return request<AdminProductPage>(`${endpoint}?${query}`);
}

export function getAdminProduct(id: number): Promise<AdminProduct> {
  return request<AdminProduct>(`${endpoint}/${encodeURIComponent(id)}`);
}

export function createAdminProduct(payload: AdminProductPayload): Promise<AdminProduct> {
  return request<AdminProduct>(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
}

export function updateAdminProduct(id: number, payload: AdminProductPayload): Promise<AdminProduct> {
  return request<AdminProduct>(`${endpoint}/${encodeURIComponent(id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
}

export function setAdminProductActive(id: number, active: boolean): Promise<AdminProduct> {
  return request<AdminProduct>(`${endpoint}/${encodeURIComponent(id)}/active`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ active }) });
}
