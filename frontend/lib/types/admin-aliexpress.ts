export interface AliExpressVariant {
  skuId: string;
  skuCode: string | null;
  attributes: Record<string, string>;
  images: string[];
  price: number | null;
  regularPrice: number | null;
  currency: string;
  availableStock: number;
}

export interface AliExpressPreview {
  fingerprint?: string;
  productId: string;
  name: string;
  description?: string;
  commercialCategory?: string;
  domesticShippingConfirmed?: boolean;
  logistics?: Record<string, string>;
  currency: string;
  images: string[];
  variants: AliExpressVariant[];
  attributes: Record<string, string>;
  warnings: string[];
}

export interface AliExpressImportRequest {
  input: string;
  shipToCountry: 'BR';
  targetCurrency: 'BRL';
  targetLanguage: 'pt_BR';
  fingerprint: string;
  name: string;
  category: string;
  variantSalePricesBrl: Record<string, string>;
}

export interface AliExpressImportResult {
  productId: number;
  supplierProductId: string;
  active: false;
  variantCount: number;
}
