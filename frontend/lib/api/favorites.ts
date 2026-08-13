import tokenStorage from '@/lib/auth/tokenStorage';
import { fetchProducts, normalizeBackendProduct, Product, BackendProductResponse } from '@/lib/products';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
const favoritesEndpoint = `${API_BASE_URL}/api/users/me/favorites`;

async function handleResponse<T>(response: Response): Promise<T> {
  const text = await response.text();

  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const errorData = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const message = errorData?.message ?? errorData?.error ?? errorData?.errors ?? text ?? response.statusText;
    throw new Error(String(message || `Erro na requisição (${response.status})`));
  }

  return data as T;
}

function authHeaders(existing?: HeadersInit) {
  const token = tokenStorage.getToken();
  return {
    ...existing,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function parseItems(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (value && typeof value === 'object' && Array.isArray((value as any).content)) return (value as any).content;
  return [];
}

function isProductObject(item: unknown): item is BackendProductResponse {
  return item !== null && typeof item === 'object' && !Array.isArray(item) && ('id' in item || 'name' in item || 'slug' in item);
}

function isProductId(item: unknown): item is string | number {
  return typeof item === 'string' || typeof item === 'number';
}

export async function getFavorites(): Promise<Product[]> {
  const response = await fetch(favoritesEndpoint, {
    method: 'GET',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    cache: 'no-store',
  });

  const data = await handleResponse<unknown>(response);
  const items = parseItems(data);

  if (!items.length) {
    return [];
  }

  if (items.every(isProductId)) {
    const ids = items.map(String);
    const allProducts = await fetchProducts().catch(() => []);
    return allProducts.filter((product) => ids.includes(product.id));
  }

  return items
    .filter(isProductObject)
    .map((item) => {
      try {
        return normalizeBackendProduct(item, undefined);
      } catch (error) {
        console.warn('Favorito ignorado por compatibilidade:', error);
        return null;
      }
    })
    .filter((product): product is Product => product !== null);
}

export async function addFavorite(productId: string): Promise<void> {
  const response = await fetch(`${favoritesEndpoint}/${encodeURIComponent(productId)}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
  });

  await handleResponse<unknown>(response);
}

export async function removeFavorite(productId: string): Promise<void> {
  const response = await fetch(`${favoritesEndpoint}/${encodeURIComponent(productId)}`, {
    method: 'DELETE',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
  });

  await handleResponse<unknown>(response);
}

export async function getFavoriteStatus(productId: string): Promise<boolean> {
  const response = await fetch(`${favoritesEndpoint}/${encodeURIComponent(productId)}/status`, {
    method: 'GET',
    headers: authHeaders(),
    cache: 'no-store',
  });

  const data = await handleResponse<unknown>(response);
  if (typeof data === 'boolean') return data;
  if (data && typeof data === 'object') {
    const status = (data as Record<string, unknown>).favorite
      ?? (data as Record<string, unknown>).isFavorite;
    if (typeof status === 'boolean') return status;
  }

  throw new Error('Resposta inválida ao consultar favorito.');
}

export async function getFavoriteCount(): Promise<number> {
  const response = await fetch(`${favoritesEndpoint}/count`, {
    method: 'GET',
    headers: authHeaders(),
    cache: 'no-store',
  });

  const data = await handleResponse<unknown>(response);
  const value = data && typeof data === 'object'
    ? (data as Record<string, unknown>).count
    : data;
  const count = typeof value === 'number' ? value : Number(value);

  if (!Number.isFinite(count) || count < 0) {
    throw new Error('Resposta inválida ao carregar contador de favoritos.');
  }

  return count;
}

export default { getFavorites, addFavorite, removeFavorite, getFavoriteStatus, getFavoriteCount };
