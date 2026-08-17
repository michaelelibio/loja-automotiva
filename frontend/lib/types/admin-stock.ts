export type AdminStockMovementType = 'INITIAL_STOCK' | 'PURCHASE_ENTRY' | 'SALE' | 'MANUAL_ADJUSTMENT_IN' | 'MANUAL_ADJUSTMENT_OUT';
export type AdminManualStockMovementType = 'PURCHASE_ENTRY' | 'MANUAL_ADJUSTMENT_IN' | 'MANUAL_ADJUSTMENT_OUT';

export type AdminStockSummary = {
  totalProducts: number;
  totalUnits: number;
  outOfStockProducts: number;
};

export type AdminStockMovement = {
  id: number;
  productId: number;
  productName: string;
  sku: string;
  type: AdminStockMovementType;
  quantity: number;
  previousStock: number;
  newStock: number;
  reason: string;
  referenceType: 'ORDER' | null;
  referenceId: number | null;
  performedBy: { userId: number; name: string; email: string } | null;
  createdAt: string;
};

export type AdminStockMovementPage = {
  content: AdminStockMovement[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdminStockMovementFilters = {
  productId?: number;
  type?: AdminStockMovementType;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  size: number;
};

export type AdminStockMovementPayload = {
  productId: number;
  type: AdminManualStockMovementType;
  quantity: number;
  reason: string;
};

export const adminStockMovementLabels: Record<AdminStockMovementType, string> = {
  INITIAL_STOCK: 'Estoque inicial',
  PURCHASE_ENTRY: 'Entrada',
  SALE: 'Venda',
  MANUAL_ADJUSTMENT_IN: 'Ajuste de entrada',
  MANUAL_ADJUSTMENT_OUT: 'Ajuste de saída',
};
