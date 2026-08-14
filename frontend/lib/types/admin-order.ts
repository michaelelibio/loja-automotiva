export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'EXPIRED';
export type AdminPaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED' | 'FAILED';

export type AdminOrderSummary = {
  id: number; status: AdminOrderStatus; subtotal: number; shippingCost: number; total: number;
  createdAt: string; updatedAt: string; processingAt: string | null; shippedAt: string | null; deliveredAt: string | null;
};

export type AdminOrderPage = {
  content: AdminOrderSummary[]; page: number; size: number; totalElements: number; totalPages: number;
};

export type AdminOrder = AdminOrderSummary & {
  expiresAt: string | null;
  customer: { userId: number; name: string; email: string };
  shippingAddress: { recipientName: string; zipCode: string; street: string; number: string; complement: string | null; neighborhood: string; city: string; state: string };
  items: Array<{ productId: number; productName: string; productSlug: string; unitPrice: number; quantity: number; subtotal: number }>;
  payment: { method: 'PIX'; status: AdminPaymentStatus; paidAt: string | null } | null;
};

export const adminOrderStatusLabels: Record<AdminOrderStatus, string> = {
  PENDING_PAYMENT: 'Aguardando pagamento', PAID: 'Pago', PROCESSING: 'Em preparação', SHIPPED: 'Enviado',
  DELIVERED: 'Entregue', CANCELED: 'Cancelado', EXPIRED: 'Expirado',
};
