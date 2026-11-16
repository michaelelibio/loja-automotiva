import { normalizeBackendProduct, type BackendProductResponse, type Product } from '@/lib/products';
import type { Category } from '@/lib/catalog-types';
export type CatalogParams = Record<string, string>;
export type CatalogResult = { content: Product[]; page: number; size: number; totalElements: number; totalPages: number; facets: { categories: Category[]; brands: string[]; surfaces: string[]; purposes: string[]; usages: string[]; origins: string[]; types: string[]; minPrice: number | null; maxPrice: number | null; hasConcentrated: boolean; hasRefill: boolean } };
const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const keys = ['q','category','subcategory','legacyCategory','brand','surface','purpose','minPrice','maxPrice','usage','origin','concentrated','refill','type','section','sort','page','size'];
export function catalogParams(raw: Record<string, string | string[] | undefined>): CatalogParams {
  const result: CatalogParams = {};
  for (const key of keys) if (typeof raw[key] === 'string' && raw[key].trim()) result[key] = raw[key];
  if (!result.q && typeof raw.busca === 'string') result.q = raw.busca;
  if (!result.category && typeof raw.categoria === 'string') result.legacyCategory = raw.categoria;
  return result;
}
export function catalogHref(path: string, params: CatalogParams, changes: CatalogParams = {}): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries({ ...params, ...changes })) if (keys.includes(key) && value) query.set(key, value);
  return path + (query.size ? `?${query}` : '');
}
export function catalogReturnTo(value?: string): string {
  return value && /^\/(?:produtos(?:\?|$)|categorias\/[^?#]*)(?:.*)$/.test(value) && !value.includes('\\') ? value : '/produtos';
}
async function read<T>(path: string): Promise<T> {
  const response = await fetch(`${base}/api/catalog${path}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Não foi possível consultar o catálogo (${response.status}).`);
  return response.json();
}
export async function fetchCatalog(params: CatalogParams = {}): Promise<CatalogResult> {
  const data = await read<Omit<CatalogResult, 'content'> & { content: BackendProductResponse[] }>(`?${new URLSearchParams(params)}`);
  return { ...data, content: data.content.map((p) => normalizeBackendProduct(p)) };
}
export function fetchCategories(): Promise<Category[]> { return read('/categories'); }
export function resolveCategory(path: string): Promise<Category> { return read(`/categories/resolve?path=${encodeURIComponent(path)}`); }
