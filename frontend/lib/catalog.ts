import { normalizeBackendProduct, type BackendProductResponse, type Product } from '@/lib/products';
import type { Category } from '@/lib/catalog-types';
export type CatalogParams = Record<string, string>;
export type CatalogResult = { content: Product[]; page: number; size: number; totalElements: number; totalPages: number; facets: { categories: Category[]; brands: string[]; surfaces: string[]; purposes: string[]; usages: string[]; origins: string[]; types: string[]; minPrice: number | null; maxPrice: number | null; hasConcentrated: boolean; hasRefill: boolean } };
const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const keys = ['q','category','subcategory','legacyCategory','brand','surface','purpose','minPrice','maxPrice','usage','origin','concentrated','refill','type','section','sort','page','size'];
export const CATALOG_PAGE_SIZE = 12;
export function catalogParams(raw: Record<string, string | string[] | undefined>): CatalogParams {
  const result: CatalogParams = {};
  for (const key of keys) if (typeof raw[key] === 'string' && raw[key].trim()) result[key] = raw[key];
  if (!result.q && typeof raw.busca === 'string') result.q = raw.busca;
  if (!result.category && typeof raw.categoria === 'string') result.legacyCategory = raw.categoria;
  if (result.page && (!/^\d+$/.test(result.page) || Number(result.page) > 1000000)) delete result.page;
  // Public listing always shows 12 items; shelves can still request their own size.
  delete result.size;
  return result;
}
export function catalogHref(path: string, params: CatalogParams, changes: CatalogParams = {}): string {
  const query = new URLSearchParams();
  const next = { ...params, ...changes };
  if (Object.keys(changes).some((key) => key !== 'page') && !('page' in changes)) delete next.page;
  for (const [key, value] of Object.entries(next)) if (keys.includes(key) && value) query.set(key, value);
  return path + (query.size ? `?${query}` : '');
}
export function catalogFilterParams(entries: Iterable<[string, FormDataEntryValue]>): CatalogParams {
  const raw = Object.fromEntries(Array.from(entries).filter(([, value]) => typeof value === 'string'));
  const params = catalogParams(raw as Record<string, string>);
  delete params.page;
  if (params.subcategory && params.category && !params.subcategory.startsWith(`${params.category}/`)) delete params.subcategory;
  return params;
}
// Page indexes remain zero based in the API and existing shareable URLs.
export function catalogPages(page: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
  const start = Math.max(1, Math.min(page - 1, totalPages - 4));
  const end = Math.min(totalPages - 2, Math.max(page + 1, 3));
  const pages: (number | 'ellipsis')[] = [0];
  if (start > 1) pages.push('ellipsis');
  for (let i = start; i <= end; i++) pages.push(i);
  if (end < totalPages - 2) pages.push('ellipsis');
  pages.push(totalPages - 1);
  return pages;
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
  const data = await read<Omit<CatalogResult, 'content'> & { content: BackendProductResponse[] }>(`?${new URLSearchParams({ size: String(CATALOG_PAGE_SIZE), ...params })}`);
  return { ...data, content: data.content.map((p) => normalizeBackendProduct(p)) };
}
export function fetchCategories(): Promise<Category[]> { return read('/categories'); }
export function resolveCategory(path: string): Promise<Category> { return read(`/categories/resolve?path=${encodeURIComponent(path)}`); }
