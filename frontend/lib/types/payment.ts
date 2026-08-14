export type PaymentMethod = 'PIX';
export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED' | 'FAILED';

export type Payment = {
  id: number;
  orderId: number;
  method: PaymentMethod;
  status: PaymentStatus;
  providerPaymentId: string | null;
  qrCode: string | null;
  qrCodeBase64: string | null;
  expiresAt: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
};
