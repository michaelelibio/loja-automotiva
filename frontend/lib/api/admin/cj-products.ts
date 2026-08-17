import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminCjImportedProduct, AdminCjProduct, AdminCjProductPage } from '@/lib/types/admin-cj-product';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/admin/integrations/cj/products`;

export class AdminCjApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'AdminCjApiError';
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminCjApiError('Sessão não encontrada.', 401);
  let response: Response;
  try {
    response = await fetch(url, { ...init, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, ...init?.headers } });
  } catch {
    throw new AdminCjApiError('Não foi possível conectar ao backend.', 0);
  }
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new AdminCjApiError(String(object?.message || object?.error || 'Não foi possível concluir a operação.'), response.status);
  }
  return data as T;
}

export function searchCjProducts(keyword: string, page: number, size = 10): Promise<AdminCjProductPage> {
  const query = new URLSearchParams({ keyword, page: String(page), size: String(size) });
  return request<AdminCjProductPage>(`${endpoint}?${query}`);
}

export function getCjProduct(productId: string): Promise<AdminCjProduct> {
  return request<AdminCjProduct>(`${endpoint}/${encodeURIComponent(productId)}`);
}

export function importCjProduct(productId: string): Promise<AdminCjImportedProduct> {
  return request<AdminCjImportedProduct>(`${endpoint}/${encodeURIComponent(productId)}/import`, { method: 'POST' });
}
