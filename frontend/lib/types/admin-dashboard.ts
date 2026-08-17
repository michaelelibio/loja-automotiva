import type { AdminOrderStatus } from '@/lib/types/admin-order';

export type AdminDashboardSummary = {
  revenueToday: number;
  ordersToday: number;
  averageTicketToday: number;
  pendingPayment: number;
  processing: number;
  shipped: number;
};

export type AdminDashboardRecentOrder = {
  orderId: number;
  customer: { userId: number; name: string; email: string };
  total: number;
  status: AdminOrderStatus;
  createdAt: string;
};

export type AdminDashboardDailyRevenue = { date: string; revenue: number };
export type AdminDashboardStatusQuantity = { status: AdminOrderStatus; quantity: number };

export type AdminDashboard = {
  summary: AdminDashboardSummary;
  recentOrders: AdminDashboardRecentOrder[];
  revenueLast7Days: AdminDashboardDailyRevenue[];
  ordersByStatus: AdminDashboardStatusQuantity[];
};
