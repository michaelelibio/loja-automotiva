import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminStockMovement, AdminStockMovementFilters, AdminStockMovementPage, AdminStockMovementPayload, AdminStockSummary } from '@/lib/types/admin-stock';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/stock`;

export class AdminStockApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly fields: Record<string, string> = {}) {
    super(message);
    this.name = 'AdminStockApiError';
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminStockApiError('Sessão não encontrada.', 401);
  const response = await fetch(url, { ...init, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, ...init?.headers } });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? object.fields as Record<string, string> : {};
    throw new AdminStockApiError(String(object?.message || object?.error || 'Não foi possível concluir a operação.'), response.status, fields);
  }
  return data as T;
}

export function getAdminStockSummary(): Promise<AdminStockSummary> {
  return request<AdminStockSummary>(`${endpoint}/summary`);
}

export function getAdminStockMovements(filters: AdminStockMovementFilters): Promise<AdminStockMovementPage> {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
  if (filters.productId !== undefined) query.set('productId', String(filters.productId));
  if (filters.type) query.set('type', filters.type);
  if (filters.dateFrom) query.set('dateFrom', filters.dateFrom);
  if (filters.dateTo) query.set('dateTo', filters.dateTo);
  return request<AdminStockMovementPage>(`${endpoint}/movements?${query}`);
}

export function createAdminStockMovement(payload: AdminStockMovementPayload): Promise<AdminStockMovement> {
  return request<AdminStockMovement>(`${endpoint}/movements`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
}
