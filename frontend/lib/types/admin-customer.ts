import type { AdminOrderStatus, AdminPaymentStatus } from '@/lib/types/admin-order';

export type AdminCustomerAuthProvider = 'LOCAL' | 'GOOGLE';

export type AdminCustomerSummary = {
  id: number;
  name: string;
  email: string;
  authProvider: AdminCustomerAuthProvider;
  active: boolean;
  emailVerified: boolean;
  createdAt: string;
  totalOrders: number;
  confirmedOrders: number;
  totalSpent: number;
  averageTicket: number;
  lastOrderAt: string | null;
};

export type AdminCustomerPage = {
  content: AdminCustomerSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdminCustomerAddress = {
  id: number; label: string | null; recipientName: string; zipCode: string; street: string;
  number: string; complement: string | null; neighborhood: string; city: string; state: string; primary: boolean;
};

export type AdminCustomerVehicle = {
  id: number; brand: string; model: string; year: number; version: string | null;
  licensePlate: string | null; primary: boolean; imageUrl: string | null;
};

export type AdminCustomerOrder = {
  id: number;
  createdAt: string;
  status: AdminOrderStatus;
  total: number;
  payment: { method: 'PIX' | 'MERCADO_PAGO'; status: AdminPaymentStatus; paidAt: string | null } | null;
};

export type AdminCustomerOrderPage = {
  content: AdminCustomerOrder[]; page: number; size: number; totalElements: number; totalPages: number;
};

export type AdminCustomerDetail = {
  customer: { id: number; name: string; email: string; authProvider: AdminCustomerAuthProvider; active: boolean; emailVerified: boolean; createdAt: string };
  addresses: AdminCustomerAddress[];
  vehicles: AdminCustomerVehicle[];
  purchaseSummary: { totalOrders: number; confirmedOrders: number; totalSpent: number; averageTicket: number; lastOrderAt: string | null };
  orders: AdminCustomerOrderPage;
};

export type AdminCustomerFilters = {
  search?: string;
  hasOrders?: boolean;
  authProvider?: AdminCustomerAuthProvider;
  page: number;
  size: number;
};

export const adminCustomerAuthProviderLabels: Record<AdminCustomerAuthProvider, string> = {
  LOCAL: 'E-mail e senha',
  GOOGLE: 'Google',
};
