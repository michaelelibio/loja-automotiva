import type { QuoteSummary } from './order';
export type AdminOrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'EXPIRED';
export type AdminPaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED' | 'FAILED';
export type AdminFulfillmentStatus = 'NOT_REQUIRED' | 'PENDING' | 'PROCESSING' | 'CREATED' | 'FAILED' | 'VERIFICATION_REQUIRED';

export type AdminAliExpressOrder = {
  orderId: number; applicable: boolean; paymentConfirmed: boolean; canSend: boolean;
  status: AdminFulfillmentStatus; blockReason: string | null;
  supplierStatus: string | null;
  supplierOrderIds: string[]; lastError: string | null;
  submittedAt: string | null; createdExternallyAt: string | null;
  supplierSync?: { startedAt: string; checkedAt: string | null; inProgress: boolean; parts: SupplierOrderPart[] } | null;
};

export type SupplierOrderPart = { orderId: string; state: string; orderStatus: string | null;
  logisticsStatus: string | null; verified: boolean; checkedAt: string; failureCode: string | null };

export type AdminOrderFulfillment = {
  orderId: number; status: AdminFulfillmentStatus; provider: string;
  supplierOrderId: string | null; supplierShipmentOrderId: string | null;
  supplierOrderIds?: string[]; supplierStatus?: string | null;
  attemptCount: number; lastError: string | null; processingStartedAt: string | null;
  createdExternallyAt: string | null; updatedAt: string;
};

export type AdminOrderSummary = {
  quote?: QuoteSummary | null;
  id: number; status: AdminOrderStatus; subtotal: number; shippingCost: number; total: number;
  createdAt: string; updatedAt: string; processingAt: string | null; shippedAt: string | null; deliveredAt: string | null;
};

export type AdminOrderPage = {
  content: AdminOrderSummary[]; page: number; size: number; totalElements: number; totalPages: number;
};

export type AdminOrder = AdminOrderSummary & {
  quote?: QuoteSummary | null;
  recipientData?: { required: boolean; complete: boolean; pendingFields: string[] };
  expiresAt: string | null;
  shippingCode: string;
  shippingName: string;
  shippingEstimatedDays: number;
  shippingProvider: string | null;
  shippingProviderAmount: number | null;
  shippingProviderCurrency: string | null;
  shippingLegs: Array<{ provider: string; code: string; name: string; originCountry: string;
    amount: number; currency: string; priceBrl: number; estimatedDays: number;
    supplierVariantIds: string[] }>;
  customer: { userId: number; name: string; email: string };
  shippingAddress: { recipientName: string; zipCode: string; street: string; number: string; complement: string | null; neighborhood: string; city: string; state: string };
  items: Array<{ productId: number; productName: string; productSlug: string;
    productVariantId: number | null; variantName: string | null;
    fulfillmentType: 'LOCAL_STOCK' | 'DROPSHIPPING' | null;
    supplier: string | null; supplierProductId: string | null; supplierVariantId: string | null;
    supplierSku: string | null; supplierCost: number | null; supplierCostCurrency: string | null;
    weightGrams: number | null; lengthMm: number | null; widthMm: number | null; heightMm: number | null;
    unitPrice: number; quantity: number; subtotal: number }>;
  payment: { method: 'PIX' | 'MERCADO_PAGO'; status: AdminPaymentStatus; paidAt: string | null } | null;
};

export const adminOrderStatusLabels: Record<AdminOrderStatus, string> = {
  PENDING_PAYMENT: 'Aguardando pagamento', PAID: 'Pago', PROCESSING: 'Em preparação', SHIPPED: 'Enviado',
  DELIVERED: 'Entregue', CANCELED: 'Cancelado', EXPIRED: 'Expirado',
};
