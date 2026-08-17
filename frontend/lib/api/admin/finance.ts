import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminFinance } from '@/lib/types/admin-finance';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AdminFinanceApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'AdminFinanceApiError'; }
}

export async function getAdminFinance(dateFrom: string, dateTo: string): Promise<AdminFinance> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminFinanceApiError('Sessão não encontrada.', 401);
  const query = new URLSearchParams({ dateFrom, dateTo });
  const response = await fetch(`${API_BASE}/api/admin/finance?${query}`, { cache: 'no-store', headers: { Authorization: `Bearer ${token}` } });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new AdminFinanceApiError(String(object?.message || object?.error || 'Não foi possível carregar o financeiro.'), response.status);
  }
  return data as AdminFinance;
}
