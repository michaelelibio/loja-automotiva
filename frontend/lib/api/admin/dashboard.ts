import tokenStorage from '@/lib/auth/tokenStorage';
import type { AdminDashboard } from '@/lib/types/admin-dashboard';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AdminDashboardApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'AdminDashboardApiError';
  }
}

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const token = tokenStorage.getToken();
  if (!token) throw new AdminDashboardApiError('Sessão não encontrada.', 401);

  const response = await fetch(`${API_BASE}/api/admin/dashboard`, {
    cache: 'no-store',
    headers: { Authorization: `Bearer ${token}` },
  });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new AdminDashboardApiError(
      String(object?.message || object?.error || 'Não foi possível carregar o dashboard.'),
      response.status,
    );
  }

  return data as AdminDashboard;
}
