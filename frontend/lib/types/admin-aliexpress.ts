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
  productId: string;
  name: string;
  currency: string;
  images: string[];
  variants: AliExpressVariant[];
  attributes: Record<string, string>;
  warnings: string[];
}
