export type PaymentMethod = 'MERCADO_PAGO';
export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED' | 'FAILED';

export type Payment = {
  id: number;
  orderId: number;
  method: PaymentMethod;
  status: PaymentStatus;
  providerPaymentId: string | null;
  preferenceId: string | null;
  checkoutUrl: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
};
