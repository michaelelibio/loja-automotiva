import tokenStorage from '@/lib/auth/tokenStorage';
import type { Payment } from '@/lib/types/payment';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class PaymentApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'PaymentApiError'; }
}

export async function createMercadoPagoPayment(orderId: number): Promise<Payment> {
  const token = tokenStorage.getToken();
  if (!token) throw new PaymentApiError('Sessão não encontrada. Entre novamente.', 401);

  const response = await fetch(`${API_BASE}/api/orders/${encodeURIComponent(orderId)}/payments`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ method: 'MERCADO_PAGO' }),
  });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    throw new PaymentApiError(String(object?.message || object?.error || 'Não foi possível iniciar o pagamento.'), response.status);
  }
  return data as Payment;
}
