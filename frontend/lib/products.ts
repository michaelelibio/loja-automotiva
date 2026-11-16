import type { CatalogMetadata } from '@/lib/catalog-types';

export type Product = {
  id: string;
  productType: 'SINGLE' | 'KIT';
  slug: string;
  name: string;
  category: string;
  catalog?: CatalogMetadata;
  price: number;
  oldPrice?: number;
  description: string;
  longDescription: string;
  features: string[];
  stock: number;
  fulfillmentType: 'LOCAL_STOCK' | 'DROPSHIPPING';
  availableForSale: boolean;
  accent: string;
  image: string;
  images: string[];
  media?: ProductMedia[];
  featured?: boolean;
  requiresVariantSelection: boolean;
  requiresAliExpressRecipientData?: boolean;
  variants: ProductVariant[];
};

export type ProductVariant = {
  id: string;
  name: string;
  sku?: string;
  attributes: Record<string, string>;
  imageUrl?: string;
};

export type ProductMedia = {
  id: string;
  type: 'IMAGE' | 'VIDEO';
  url: string;
  position: number;
  altText?: string;
};

export type BackendProductResponse = {
  id: number;
  productType: 'SINGLE' | 'KIT';
  name: string;
  slug: string;
  description: string;
  longDescription: string;
  price: number;
  oldPrice: number | null;
  category: string;
  stockQuantity: number;
  imageUrl: string | null;
  active: boolean;
  fulfillmentType: 'LOCAL_STOCK' | 'DROPSHIPPING';
  availableForSale: boolean;
  requiresVariantSelection?: boolean;
  requiresAliExpressRecipientData?: boolean;
  variants?: Array<{ id: number; name: string | null; sku: string | null;
    attributes: Record<string, string> | null; imageUrl: string | null }>;
  media?: Array<{ id: number; type: 'IMAGE' | 'VIDEO'; url: string;
    position: number; altText: string | null }>;
  catalog?: CatalogMetadata;
  [key: string]: unknown;
};

type BackendProduct = BackendProductResponse;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const productsEndpoint = `${API_BASE_URL}/api/products`;

function parseNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === 'string') {
    const normalized = value.replace(/[^0-9.,-]/g, '').replace(',', '.');
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  return fallback;
}

function parseText(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number' || typeof value === 'boolean') return String(value).trim();
  return '';
}

function normalizeProductType(value: unknown, fallback: Product['productType'] = 'SINGLE'): Product['productType'] {
  const productType = parseText(value).toUpperCase();

  if (productType === 'SINGLE' || productType === 'KIT') {
    return productType;
  }

  return fallback;
}

function normalizeImages(value: unknown, fallbackImage: string): string[] {
  if (Array.isArray(value)) {
    const images = value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0);
    if (images.length > 0) return images;
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    return [value.trim()];
  }

  if (fallbackImage) {
    return [fallbackImage];
  }

  return [''];
}

function normalizeMedia(value: unknown): ProductMedia[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  return value.flatMap((candidate) => {
    if (!candidate || typeof candidate !== 'object') return [];
    const media = candidate as Record<string, unknown>;
    const id = parseText(media.id);
    const url = parseText(media.url);
    const type = parseText(media.type).toUpperCase();
    const position = parseNumber(media.position, 0);
    if (!id || !url || (type !== 'IMAGE' && type !== 'VIDEO') || seen.has(url)) return [];
    seen.add(url);
    return [{ id, type, url, position,
      altText: parseText(media.altText) || undefined } as ProductMedia];
  }).sort((left, right) => left.position - right.position);
}

function normalizeBackendProduct(payload: BackendProduct, fallback?: Product): Product {
  const payloadId = parseText(payload.id ?? payload.productId ?? payload.sku);
  // API omissions must never inherit commercial or technical facts from example products.
  void fallback;
  const id = payloadId || parseText(payload.slug);
  const slug = parseText(payload.slug ?? payload.urlSlug) || id;
  const name = parseText(payload.name ?? payload.title);
  const productType = normalizeProductType(payload.productType);
  const category = parseText(payload.category);
  const price = parseNumber(payload.price ?? payload.value ?? payload.amount);
  const oldPrice = payload.oldPrice == null ? undefined : parseNumber(payload.oldPrice);
  const description = parseText(payload.description ?? payload.shortDescription);
  const longDescription = parseText(payload.longDescription ?? payload.details) || description;
  const features = Array.isArray(payload.features)
    ? payload.features.filter((item): item is string => typeof item === 'string')
    : typeof payload.features === 'string'
      ? [payload.features]
      : [];

  const stock = typeof payload.stock === 'number' && Number.isFinite(payload.stock)
    ? payload.stock
    : typeof payload.quantity === 'number' && Number.isFinite(payload.quantity)
      ? payload.quantity
      : typeof payload.stockQuantity === 'number' && Number.isFinite(payload.stockQuantity)
        ? payload.stockQuantity
        : 0;
  const fulfillmentType = payload.fulfillmentType === 'DROPSHIPPING' ? 'DROPSHIPPING' : 'LOCAL_STOCK';
  const availableForSale = typeof payload.availableForSale === 'boolean'
    ? payload.availableForSale
    : Boolean(payload.active) && (fulfillmentType === 'DROPSHIPPING' || stock > 0);

  const accent = parseText(payload.accent ?? payload.color ?? payload.themeColor) || '#f2f2f2';
  const image = parseText(payload.image ?? payload.imageUrl ?? payload.mainImage ?? payload.thumbnail);
  const media = normalizeMedia(payload.media);
  const galleryImages = media.filter((item) => item.type === 'IMAGE').map((item) => item.url);
  const images = normalizeImages(galleryImages.length > 0
    ? galleryImages : payload.images ?? payload.gallery ?? payload.photos,
  image);
  const featured = payload.catalog?.featured ?? payload.featured === true;
  const variants = Array.isArray(payload.variants) ? payload.variants.flatMap((variant) => {
    const variantId = parseText(variant?.id);
    if (!variantId) return [];
    return [{ id: variantId, name: parseText(variant.name) || parseText(variant.sku) || `Opção ${variantId}`,
      sku: parseText(variant.sku) || undefined,
      attributes: variant.attributes && typeof variant.attributes === 'object' ? variant.attributes : {},
      imageUrl: parseText(variant.imageUrl) || undefined }];
  }) : [];
  const requiresVariantSelection = typeof payload.requiresVariantSelection === 'boolean'
    ? payload.requiresVariantSelection : variants.length > 0;

  if (!id || !slug) {
    throw new Error('Produto inválido retornado pela API: faltando id ou slug.');
  }

  return {
    id,
    productType,
    slug,
    name,
    category,
    price,
    oldPrice: oldPrice != null && oldPrice > price ? oldPrice : undefined,
    description,
    longDescription,
    features,
    stock,
    fulfillmentType,
    availableForSale,
    accent,
    image,
    images,
    media,
    featured,
    requiresVariantSelection,
    requiresAliExpressRecipientData: payload.requiresAliExpressRecipientData === true,
    variants,
    catalog: payload.catalog,
  };
}

export { normalizeBackendProduct };

async function request<T>(url: string): Promise<T> {
  const response = await fetch(url, { cache: 'no-store' });

  if (!response.ok) {
    throw new Error(`Falha ao carregar dados de produtos: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchProducts(): Promise<Product[]> {
  const data = await request<unknown>(`${productsEndpoint}?size=100&sort=id,asc`);
  const responseObject = data && typeof data === 'object' ? data as Record<string, unknown> : null;
  const items = Array.isArray(data)
    ? data
    : Array.isArray(responseObject?.content)
      ? responseObject.content
      : null;

  if (!Array.isArray(items)) {
    throw new Error('Resposta inválida da API de produtos.');
  }

  // Cart, favorites and checkout must still resolve products discovered beyond the first catalog page.
  const pages = responseObject?.totalPages;
  if (typeof pages === 'number' && Number.isSafeInteger(pages) && pages > 1) {
    if (pages > 10000) throw new Error('Catálogo muito extenso para esta consulta.');
    for (let page = 1; page < pages; page++) {
      const next = await request<{ content: unknown[] }>(`${productsEndpoint}?size=100&sort=id,asc&page=${page}`);
      if (!Array.isArray(next.content)) throw new Error('Resposta inválida da API de produtos.');
      items.push(...next.content);
    }
  }

  return items
    .map((item) => {
      try {
        const backendProduct = item && typeof item === 'object' ? item as BackendProduct : {} as BackendProduct;
        return normalizeBackendProduct(backendProduct);
      } catch (error) {
        console.warn('Produto da API ignorado por compatibilidade:', error);
        return null;
      }
    })
    .filter((product): product is Product => product !== null);
}

export async function fetchProductBySlug(slug: string): Promise<Product | null> {
  try {
    const response = await fetch(`${productsEndpoint}/slug/${encodeURIComponent(slug)}`, { cache: 'no-store' });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(`Falha ao carregar o produto: ${response.statusText}`);
    }

    const data = await response.json();
    return normalizeBackendProduct(data);
  } catch {
    const products = await fetchProducts();
    return products.find((product) => product.slug === slug) ?? null;
  }
}

