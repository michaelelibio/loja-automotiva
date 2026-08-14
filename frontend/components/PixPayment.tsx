'use client';

import { useState } from 'react';
import { createPixPayment, PaymentApiError } from '@/lib/api/payments';
import type { Payment, PaymentStatus } from '@/lib/types/payment';

const statusLabels: Record<PaymentStatus, string> = {
  PENDING: 'Aguardando pagamento',
  PAID: 'Pago',
  EXPIRED: 'Expirado',
  CANCELED: 'Cancelado',
  FAILED: 'Falhou',
};

function qrImageSource(value: string) {
  return value.startsWith('data:image/') ? value : `data:image/png;base64,${value}`;
}

export function PixPayment({ orderId }: { orderId: number }) {
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);

  async function generate() {
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      setPayment(await createPixPayment(orderId));
    } catch (cause) {
      if (cause instanceof PaymentApiError && cause.status === 409) {
        setError(cause.message || 'O prazo deste pedido expirou ou ele não está mais disponível para pagamento.');
      } else {
        setError('Não foi possível gerar o PIX agora. Tente novamente em instantes.');
      }
    } finally {
      setLoading(false);
    }
  }

  async function copyCode() {
    if (!payment?.qrCode) return;
    try {
      await navigator.clipboard.writeText(payment.qrCode);
      setCopyFeedback('Código PIX copiado.');
    } catch {
      setCopyFeedback('Não foi possível copiar automaticamente. Selecione o código acima.');
    }
  }

  return <section className="order-detail-card pix-payment-card" aria-labelledby="payment-title">
    <p className="eyebrow">PAGAMENTO</p>
    <h2 id="payment-title">Pagamento</h2>
    {!payment ? <div className="pix-payment-option">
      <div><strong>PIX</strong><span>Pagamento rápido por QR Code ou código copia e cola.</span></div>
      <button type="button" disabled={loading} onClick={() => void generate()}>{loading ? 'Gerando PIX...' : 'Gerar PIX'}</button>
    </div> : <div className="pix-payment-result">
      <div className="pix-payment-status"><span>Status do pagamento</span><strong>{statusLabels[payment.status]}</strong></div>
      {payment.qrCodeBase64 && <img className="pix-qr-code" src={qrImageSource(payment.qrCodeBase64)} alt="QR Code para pagamento via PIX" />}
      {payment.qrCode && <div className="pix-copy-area">
        <label htmlFor="pix-code">PIX copia e cola</label>
        <textarea id="pix-code" readOnly value={payment.qrCode} rows={4} onFocus={(event) => event.currentTarget.select()} />
        <button type="button" onClick={() => void copyCode()}>Copiar código PIX</button>
        {copyFeedback && <p role="status">{copyFeedback}</p>}
      </div>}
      {payment.expiresAt && <p className="pix-expiration">Expira em <strong>{new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(payment.expiresAt))}</strong></p>}
    </div>}
    {error && <p className="pix-payment-error" role="alert">{error}</p>}
  </section>;
}
