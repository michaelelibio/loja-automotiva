export type AdminProductType = 'SINGLE' | 'KIT';
export type AdminFulfillmentType = 'LOCAL_STOCK' | 'DROPSHIPPING';

export type AdminProduct = {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  longDescription: string | null;
  price: number;
  oldPrice: number | null;
  costPrice: number;
  stock: number;
  active: boolean;
  category: string;
  sku: string;
  imageUrl: string | null;
  productType: AdminProductType;
  supplier: string | null;
  supplierProductId: string | null;
  supplierCostUsd: number | null;
  supplierExchangeRate: number | null;
  supplierCostUpdatedAt: string | null;
  fulfillmentType: AdminFulfillmentType;
  availableForSale: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AdminProductPage = {
  content: AdminProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type AdminProductPayload = {
  name: string;
  slug: string;
  description: string | null;
  longDescription: string | null;
  price: number;
  oldPrice: number | null;
  costPrice: number;
  stock: number;
  active: boolean;
  category: string;
  sku: string;
  imageUrl: string | null;
  productType: AdminProductType;
};

export type AdminProductFilters = {
  search?: string;
  active?: boolean;
  category?: string;
  page: number;
  size: number;
};
