export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELED' | 'EXPIRED';
export type CreateOrderItemRequest = { productId: number; quantity: number };
export type CreateOrderRequest = { addressId: number; shippingCode: string; items: CreateOrderItemRequest[] };
export type OrderItem = { productId: number; productName: string; productSlug: string; unitPrice: number; quantity: number; subtotal: number };
export type ShippingAddress = { recipientName: string; zipCode: string; street: string; number: string; complement: string | null; neighborhood: string; city: string; state: string };
export type OrderShipping = { code: string; name: string; price: number; estimatedDays: number };
export type Order = { id: number; status: OrderStatus; subtotal: number; shippingCost: number; total: number; createdAt: string; expiresAt: string; updatedAt: string; shippingAddress: ShippingAddress; shipping: OrderShipping; items: OrderItem[] };
