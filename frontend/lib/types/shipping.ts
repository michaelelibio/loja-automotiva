export type ShippingQuoteItem = { productId: number; quantity: number };
export type ShippingQuoteRequest = { zipCode: string; items: ShippingQuoteItem[] };
export type ShippingOption = { code: string; name: string; price: number; estimatedDays: number };
export type ShippingQuoteResponse = { options: ShippingOption[] };
