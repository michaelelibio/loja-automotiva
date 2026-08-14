import tokenStorage from '@/lib/auth/tokenStorage';
import type { CreateOrderRequest, Order } from '@/lib/types/order';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/orders`;

export class OrderApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'OrderApiError'; }
}
function headers(json = false): HeadersInit {
  const token = tokenStorage.getToken();
  if (!token) throw new OrderApiError('Sessão não encontrada. Entre novamente.', 401);
  return { Authorization: `Bearer ${token}`, ...(json ? { 'Content-Type': 'application/json' } : {}) };
}
async function parse<T>(response: Response): Promise<T> {
  const text = await response.text(); let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? Object.values(object.fields as Record<string, unknown>).join(' ') : null;
    throw new OrderApiError(String(fields || object?.message || object?.error || text || response.statusText), response.status);
  }
  return data as T;
}
export async function createOrder(data: CreateOrderRequest): Promise<Order> { return parse<Order>(await fetch(endpoint, { method: 'POST', headers: headers(true), body: JSON.stringify(data) })); }
export async function getOrders(): Promise<Order[]> { return parse<Order[]>(await fetch(endpoint, { headers: headers(), cache: 'no-store' })); }
export async function getOrder(id: number): Promise<Order> { return parse<Order>(await fetch(`${endpoint}/${encodeURIComponent(id)}`, { headers: headers(), cache: 'no-store' })); }
