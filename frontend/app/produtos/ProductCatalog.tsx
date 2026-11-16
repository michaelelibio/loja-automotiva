import Link from 'next/link';
import { ProductCard } from '@/components/ProductCard';
import { catalogHref, type CatalogParams, type CatalogResult } from '@/lib/catalog';
import { catalogLabels } from '@/lib/catalog-types';
import { ResponsiveCatalogFilters } from '@/components/ResponsiveCatalogFilters';

export function ProductCatalog({ result, params, path = '/produtos' }: { result: CatalogResult; params: CatalogParams; path?: string }) {
  const f = result.facets;
  const current = catalogHref(path, params);
  const select = (name: string, label: string, options: { value: string; label: string }[]) => options.length > 0 || params[name] ? <label key={name}>{label}<select name={name} defaultValue={params[name] ?? ''}><option value="">Todos</option>{params[name] && !options.some((o) => o.value === params[name]) && <option value={params[name]}>{catalogLabels[params[name]] ?? params[name]}</option>}{options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label> : null;
  const values = (items: string[]) => items.map((value) => ({ value, label: catalogLabels[value] ?? value }));
  return <section className="catalog-layout">
    <ResponsiveCatalogFilters><form method="get" action={path} className="catalog-filter-form">
      <label>Buscar<input name="q" type="search" maxLength={200} defaultValue={params.q} placeholder="Nome, marca ou termo" /></label>
      {path === '/produtos' ? select('category', 'Categoria', f.categories.filter((c) => c.parentId === null).map((c) => ({ value: c.path, label: `${c.name} (${c.count})` }))) : params.category && <input type="hidden" name="category" value={params.category} />}
      {path === '/produtos' || !params.subcategory ? select('subcategory', 'Subcategoria', f.categories.filter((c) => c.parentId !== null && (!params.category || c.path.startsWith(`${params.category}/`))).map((c) => ({ value: c.path, label: `${c.name} (${c.count})` }))) : <input type="hidden" name="subcategory" value={params.subcategory} />}
      {params.legacyCategory && <input type="hidden" name="legacyCategory" value={params.legacyCategory} />}
      {select('brand', 'Marca', values(f.brands))}
      <div className="catalog-price-fields"><label>Preço mínimo<input type="number" step="0.01" min="0" name="minPrice" defaultValue={params.minPrice} /></label><label>Preço máximo<input type="number" step="0.01" min="0" name="maxPrice" defaultValue={params.maxPrice} /></label></div>
      {select('surface', 'Superfície', values(f.surfaces))}{select('purpose', 'Finalidade', values(f.purposes))}
      {select('usage', 'Uso', values(f.usages))}{select('origin', 'Seleção comercial', values(f.origins))}{select('type', 'Tipo', values(f.types))}
      {(f.hasConcentrated || params.concentrated) && select('concentrated', 'Concentrado', [{ value: 'true', label: 'Sim' }])}
      {(f.hasRefill || params.refill) && select('refill', 'Refil', [{ value: 'true', label: 'Sim' }])}
      {select('sort', 'Ordenar', [{ value: 'relevance', label: 'Relevância' },{ value: 'lowest', label: 'Menor preço' },{ value: 'highest', label: 'Maior preço' },{ value: 'newest', label: 'Novidades' }])}
      {params.section && <input type="hidden" name="section" value={params.section} />}
      <button className="primary-button" type="submit">Aplicar filtros</button><Link href={path}>Limpar filtros</Link>
    </form></ResponsiveCatalogFilters>
    <div className="catalog-results"><p role="status">{result.totalElements} produto{result.totalElements === 1 ? '' : 's'} encontrado{result.totalElements === 1 ? '' : 's'}</p>
      {result.content.length ? <div className="products-grid">{result.content.map((product) => <ProductCard key={product.id} product={product} returnTo={current} />)}</div> : <div className="empty-catalog"><h2>Nenhum produto encontrado</h2><p>Ajuste a busca ou remova alguns filtros.</p><Link href={path}>Limpar filtros</Link></div>}
      {result.totalPages > 1 && <nav className="catalog-pagination" aria-label="Paginação">{result.page > 0 && <Link href={catalogHref(path, params, { page: String(result.page - 1) })}>← Anterior</Link>}<span>Página {result.page + 1} de {result.totalPages}</span>{result.page + 1 < result.totalPages && <Link href={catalogHref(path, params, { page: String(result.page + 1) })}>Próxima →</Link>}</nav>}
    </div>
  </section>;
}
