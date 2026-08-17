'use client';

import { useState } from 'react';
import { createMercadoPagoPayment, PaymentApiError } from '@/lib/api/payments';

export function MercadoPagoPayment({ orderId }: { orderId: number }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function proceed() {
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      const payment = await createMercadoPagoPayment(orderId);
      if (!payment.checkoutUrl) throw new PaymentApiError('Link de pagamento indisponível.', 502);
      window.location.assign(payment.checkoutUrl);
    } catch (cause) {
      if (cause instanceof PaymentApiError && cause.status === 409) {
        setError(cause.message || 'O prazo deste pedido expirou ou ele não está mais disponível para pagamento.');
      } else if (cause instanceof PaymentApiError && cause.status === 401) {
        setError('Sua sessão expirou. Entre novamente para continuar.');
      } else {
        setError('Não foi possível iniciar o pagamento agora. Tente novamente em instantes.');
      }
      setLoading(false);
    }
  }

  return <section className="order-detail-card payment-checkout-card" aria-labelledby="payment-title">
    <p className="eyebrow">PAGAMENTO</p>
    <h2 id="payment-title">Mercado Pago</h2>
    <div className="payment-checkout-option">
      <div>
        <strong>PIX, cartões e outros meios de pagamento</strong>
        <span>Você será direcionado ao ambiente seguro do Mercado Pago para concluir o pagamento.</span>
      </div>
      <button type="button" disabled={loading} onClick={() => void proceed()}>
        {loading ? 'Abrindo pagamento...' : 'Ir para pagamento'}
      </button>
    </div>
    {error && <p className="payment-checkout-error" role="alert">{error}</p>}
  </section>;
}
