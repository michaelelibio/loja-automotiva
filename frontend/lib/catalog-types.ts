export type Category = { id: number; name: string; slug: string; path: string; description?: string | null; parentId: number | null; position: number; active: boolean; count: number };
export type CatalogMetadata = {
  category: Pick<Category, 'id' | 'name' | 'slug' | 'path'> | null;
  subcategory: Pick<Category, 'id' | 'name' | 'slug' | 'path'> | null;
  brand: string | null; surfaces: string[]; purposes: string[]; searchTerms: string[];
  usage: 'DOMESTIC' | 'PROFESSIONAL' | 'BOTH' | null;
  concentrated: boolean | null; refill: boolean | null; featured: boolean; beginner: boolean;
  commercialOrigin: 'NATIONAL' | 'INTERNATIONAL' | null; publishedAt: string | null;
};
export type CatalogInput = Omit<CatalogMetadata, 'category' | 'subcategory' | 'publishedAt'> & { categoryId: number | null; subcategoryId: number | null };
export const emptyCatalog: CatalogInput = { categoryId: null, subcategoryId: null, brand: null, surfaces: [], purposes: [], searchTerms: [], usage: null, concentrated: null, refill: null, featured: false, beginner: false, commercialOrigin: null };
export function catalogInput(metadata?: CatalogMetadata): CatalogInput {
  if (!metadata) return { ...emptyCatalog };
  const { category, subcategory, publishedAt: _date, ...fields } = metadata;
  void _date;
  return { ...fields, categoryId: category?.id ?? null, subcategoryId: subcategory?.id ?? null };
}
export const catalogLabels: Record<string, string> = { DOMESTIC: 'Uso doméstico', PROFESSIONAL: 'Uso profissional', BOTH: 'Uso doméstico e profissional', NATIONAL: 'Nacional', INTERNATIONAL: 'Internacional', SINGLE: 'Produto individual', KIT: 'Kit' };
