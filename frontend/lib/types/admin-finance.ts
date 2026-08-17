import type { AdminOrderStatus, AdminPaymentStatus } from '@/lib/types/admin-order';

export type AdminFinanceCostCoverage = { complete: boolean; ordersWithUnknownCost: number };
export type AdminFinanceProductSales = { productId: number; name: string; quantitySold: number; revenue: number };

export type AdminFinance = {
  period: { dateFrom: string; dateTo: string };
  summary: { revenue: number; confirmedOrders: number; averageTicket: number; knownProductCost: number; grossProfit: number; grossMargin: number };
  costCoverage: AdminFinanceCostCoverage;
  daily: Array<{ date: string; revenue: number; knownProductCost: number; grossProfit: number; costCoverage: AdminFinanceCostCoverage }>;
  paymentMethods: Array<{ method: string; orders: number; revenue: number }>;
  ordersByStatus: Array<{ status: AdminOrderStatus; quantity: number }>;
  topSellingProducts: AdminFinanceProductSales[];
  lowestSellingProducts: AdminFinanceProductSales[];
  recentTransactions: Array<{
    orderId: number;
    customer: { userId: number; name: string; email: string };
    status: AdminOrderStatus;
    paymentMethod: string | null;
    paymentStatus: AdminPaymentStatus | null;
    total: number;
    knownProductCost: number;
    grossProfit: number;
    costComplete: boolean;
    createdAt: string;
    paidAt: string | null;
  }>;
};
