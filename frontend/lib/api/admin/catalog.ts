import { request } from './products';
import type { CatalogInput, Category } from '@/lib/catalog-types';
const base = `${process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/admin/catalog`;
export type CategoryInput = Pick<Category, 'name' | 'slug' | 'description' | 'parentId' | 'position' | 'active'>;
export const getCategories = () => request<Category[]>(`${base}/categories`);
export const saveCategory = (id: number | null, value: CategoryInput) => request<Category>(`${base}/categories${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
export function cleanCatalog(value: CatalogInput): CatalogInput {
  const list = (entries: string[]) => [...new Set(entries.map((v) => v.trim()).filter(Boolean))];
  return { ...value, brand: value.brand?.trim() || null, surfaces: list(value.surfaces), purposes: list(value.purposes), searchTerms: list(value.searchTerms) };
}
export const bulkClassify = (productIds: number[], fields: string[], catalog: CatalogInput) => request<void>(`${base}/products`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ productIds, fields, catalog: cleanCatalog(catalog) }) });
