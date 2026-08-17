export interface AdminCjProduct {
  cjProductId: string;
  name: string | null;
  imageUrl: string | null;
  priceUsd: number | null;
  categoryId: string | null;
  categoryName: string | null;
  sku: string | null;
}

export interface AdminCjProductPage {
  page: number;
  size: number;
  totalRecords: number;
  totalPages: number;
  products: AdminCjProduct[];
}

export interface AdminCjImportedProduct {
  id: number;
  name: string;
  slug: string;
  sku: string;
  imageUrl: string | null;
  supplier: string;
  supplierProductId: string;
  supplierCostUsd: number;
  supplierExchangeRate: number;
  supplierCostUpdatedAt: string;
  costPrice: number;
  price: number;
  category: string;
  stock: number;
  active: boolean;
}
