export type ShippingQuoteItem = { productId: number; variantId?: number; quantity: number };
export type ShippingQuoteRequest = { zipCode: string; items: ShippingQuoteItem[] };
export type ShippingLeg = { provider: string; code: string; name: string; originCountry: string;
  price: number; estimatedDays: number };
export type ShippingOption = { code: string; name: string; price: number; estimatedDays: number;
  provider: string; legs: ShippingLeg[] };
export type ShippingQuoteResponse = { options: ShippingOption[] };
