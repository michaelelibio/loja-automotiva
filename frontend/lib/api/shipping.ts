import tokenStorage from '@/lib/auth/tokenStorage';
import type { ShippingQuoteRequest, ShippingQuoteResponse } from '@/lib/types/shipping';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class ShippingApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'ShippingApiError'; }
}

export async function quoteShipping(data: ShippingQuoteRequest, signal?: AbortSignal): Promise<ShippingQuoteResponse> {
  const token = tokenStorage.getToken();
  if (!token) throw new ShippingApiError('Sessão não encontrada. Entre novamente.', 401);
  const response = await fetch(`${API_BASE}/api/shipping/quote`, {
    method: 'POST', signal,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  const text = await response.text();
  let result: unknown = null;
  try { result = text ? JSON.parse(text) : null; } catch { result = text; }
  if (!response.ok) {
    const object = result && typeof result === 'object' ? result as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? Object.values(object.fields as Record<string, unknown>).join(' ') : null;
    throw new ShippingApiError(String(fields || object?.message || object?.error || 'Não foi possível calcular o frete.'), response.status);
  }
  return result as ShippingQuoteResponse;
}
