import { products as fallbackProducts } from '@/data/products';

export type Product = {
  id: string;
  slug: string;
  name: string;
  category: 'Lavagem' | 'Proteção' | 'Detalhamento' | 'Acessórios';
  price: number;
  oldPrice?: number;
  description: string;
  longDescription: string;
  features: string[];
  stock: number;
  accent: string;
  image: string;
  images: string[];
  featured?: boolean;
};

type BackendProduct = Record<string, unknown>;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
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

function normalizeCategory(value: unknown, fallback: Product['category']): Product['category'] {
  const raw = parseText(value).toLocaleLowerCase('pt-BR');
  if (raw.includes('lav')) return 'Lavagem';
  if (raw.includes('prote')) return 'Proteção';
  if (raw.includes('detal') || raw.includes('estética')) return 'Detalhamento';
  if (raw.includes('acess')) return 'Acessórios';
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

function normalizeBackendProduct(payload: BackendProduct, fallback?: Product): Product {
  const fallbackSource = fallback ?? fallbackProducts.find((item) => item.slug === payload.slug || item.id === payload.id || item.name === payload.name);

  const id = parseText(payload.id ?? payload.productId ?? payload.sku) || parseText(payload.slug) || fallbackSource?.id || '';
  const slug = parseText(payload.slug ?? payload.urlSlug ?? payload.name) || id || fallbackSource?.slug || '';
  const name = parseText(payload.name ?? payload.title) || fallbackSource?.name || 'Produto sem nome';
  const category = normalizeCategory(payload.category ?? payload.type ?? payload.genre, fallbackSource?.category ?? 'Acessórios');
  const price = parseNumber(payload.price ?? payload.value ?? payload.amount, fallbackSource?.price ?? 0);
  const oldPrice = parseNumber(payload.oldPrice ?? payload.previousPrice ?? payload.listPrice, fallbackSource?.oldPrice ?? undefined);
  const description = parseText(payload.description ?? payload.shortDescription) || fallbackSource?.description || 'Descrição indisponível';
  const longDescription = parseText(payload.longDescription ?? payload.details) || fallbackSource?.longDescription || description;
  const features = Array.isArray(payload.features)
    ? payload.features.filter((item): item is string => typeof item === 'string')
    : typeof payload.features === 'string'
      ? [payload.features]
      : fallbackSource?.features ?? [];

  const stock = typeof payload.stock === 'number' && Number.isFinite(payload.stock)
    ? payload.stock
    : typeof payload.quantity === 'number' && Number.isFinite(payload.quantity)
      ? payload.quantity
      : typeof payload.stockQuantity === 'number' && Number.isFinite(payload.stockQuantity)
        ? payload.stockQuantity
        : fallbackSource?.stock ?? 1;

  const accent = parseText(payload.accent ?? payload.color ?? payload.themeColor) || fallbackSource?.accent || '#f2f2f2';
  const image = parseText(payload.image ?? payload.imageUrl ?? payload.mainImage ?? payload.thumbnail) || fallbackSource?.image || '';
  const images = normalizeImages(payload.images ?? payload.gallery ?? payload.photos, image || fallbackSource?.image || '');
  const featured = typeof payload.featured === 'boolean'
    ? payload.featured
    : typeof payload.highlighted === 'boolean'
      ? payload.highlighted
      : fallbackSource?.featured ?? false;

  if (!id || !slug) {
    throw new Error('Produto inválido retornado pela API: faltando id ou slug.');
  }

  return {
    id,
    slug,
    name,
    category,
    price,
    oldPrice: Number.isFinite(oldPrice) ? oldPrice : undefined,
    description,
    longDescription,
    features,
    stock,
    accent,
    image,
    images,
    featured,
  };
}

async function request<T>(url: string): Promise<T> {
  const response = await fetch(url, { cache: 'no-store' });

  if (!response.ok) {
    throw new Error(`Falha ao carregar dados de produtos: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchProducts(): Promise<Product[]> {
  const data = await request<unknown>(productsEndpoint);
  const items = Array.isArray(data)
    ? data
    : (data && typeof data === 'object' && Array.isArray((data as any).content))
      ? (data as any).content
      : null;

  if (!Array.isArray(items)) {
    throw new Error('Resposta inválida da API de produtos.');
  }

  return items
    .map((item) => {
      try {
        return normalizeBackendProduct(item, fallbackProducts.find((fallback) => fallback.slug === (item as any)?.slug || fallback.id === String((item as any)?.id)));
      } catch (error) {
        console.warn('Produto da API ignorado por compatibilidade:', error);
        return null;
      }
    })
    .filter((product): product is Product => product !== null);
}

export async function fetchProductBySlug(slug: string): Promise<Product | null> {
  const response = await fetch(`${productsEndpoint}/slug/${encodeURIComponent(slug)}`, { cache: 'no-store' });

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Falha ao carregar o produto: ${response.statusText}`);
  }

  const data = await response.json();
  return normalizeBackendProduct(data, fallbackProducts.find((fallback) => fallback.slug === slug));
}

