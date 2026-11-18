'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState, useTransition, type FormEvent } from 'react';
import { ProductCard } from '@/components/ProductCard';
import { catalogFilterParams, catalogHref, catalogPages, type CatalogParams, type CatalogResult } from '@/lib/catalog';
import { catalogLabels } from '@/lib/catalog-types';
import { ResponsiveCatalogFilters } from '@/components/ResponsiveCatalogFilters';

export function ProductCatalog({ result, params, path = '/produtos' }: { result: CatalogResult; params: CatalogParams; path?: string }) {
  const f = result.facets;
  const current = catalogHref(path, params);
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [action, setAction] = useState<'apply' | 'clear' | 'page'>('page');
  const resultsRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (window.location.hash === '#catalog-results') {
      resultsRef.current?.scrollIntoView({ block: 'start' });
      resultsRef.current?.focus({ preventScroll: true });
    }
  }, [current]);
  function navigate(href: string, nextAction: typeof action) {
    if (pending) return;
    setAction(nextAction);
    startTransition(() => {
      router.push(`${href}#catalog-results`, { scroll: false });
      if (href === current) router.refresh();
    });
  }
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const min = form.elements.namedItem('minPrice') as HTMLInputElement;
    const max = form.elements.namedItem('maxPrice') as HTMLInputElement;
    max.setCustomValidity(min.value && max.value && Number(min.value) > Number(max.value) ? 'O preço máximo deve ser maior ou igual ao mínimo.' : '');
    if (!form.reportValidity()) return;
    navigate(catalogHref(path, catalogFilterParams(new FormData(form).entries())), 'apply');
  }
  const select = (name: string, label: string, options: { value: string; label: string }[]) => options.length > 0 || params[name] ? <label key={name}>{label}<select name={name} defaultValue={params[name] ?? ''} onChange={(event) => {
    if (name === 'category') {
      const subcategory = event.currentTarget.form?.elements.namedItem('subcategory') as HTMLSelectElement | null;
      if (subcategory) subcategory.value = '';
    }
    if (name === 'subcategory' && path === '/produtos' && event.currentTarget.value) {
      const category = event.currentTarget.form?.elements.namedItem('category') as HTMLSelectElement | null;
      if (category) category.value = event.currentTarget.value.split('/')[0];
    }
    if (name === 'sort') event.currentTarget.form?.requestSubmit();
  }}><option value="">Todos</option>{params[name] && !options.some((o) => o.value === params[name]) && <option value={params[name]}>{catalogLabels[params[name]] ?? params[name]}</option>}{options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label> : null;
  const values = (items: string[]) => items.map((value) => ({ value, label: catalogLabels[value] ?? value }));
  return <section className="catalog-layout" aria-busy={pending}>
    <ResponsiveCatalogFilters><form method="get" action={path} onSubmit={apply} onInput={(event) => {
      const max = event.currentTarget.elements.namedItem('maxPrice') as HTMLInputElement;
      max.setCustomValidity('');
    }} className="catalog-filter-form">
      <label>Buscar<input name="q" type="search" maxLength={200} defaultValue={params.q} placeholder="Nome, marca ou termo" /></label>
      {path === '/produtos' ? select('category', 'Categoria', f.categories.filter((c) => c.parentId === null).map((c) => ({ value: c.path, label: `${c.name} (${c.count})` }))) : params.category && <input type="hidden" name="category" value={params.category} />}
      {path === '/produtos' || !params.subcategory ? select('subcategory', 'Subcategoria', f.categories.filter((c) => c.parentId !== null && (path === '/produtos' || !params.category || c.path.startsWith(`${params.category}/`))).map((c) => ({ value: c.path, label: `${c.name} (${c.count})` }))) : <input type="hidden" name="subcategory" value={params.subcategory} />}
      {params.legacyCategory && <input type="hidden" name="legacyCategory" value={params.legacyCategory} />}
      {select('brand', 'Marca', values(f.brands))}
      <div className="catalog-price-fields"><label>Preço mínimo<input type="number" step="0.01" min="0" name="minPrice" defaultValue={params.minPrice} /></label><label>Preço máximo<input type="number" step="0.01" min="0" name="maxPrice" defaultValue={params.maxPrice} /></label></div>
      {select('surface', 'Superfície', values(f.surfaces))}{select('purpose', 'Finalidade', values(f.purposes))}
      {select('usage', 'Uso', values(f.usages))}{select('origin', 'Seleção comercial', values(f.origins))}{select('type', 'Tipo', values(f.types))}
      {(f.hasConcentrated || params.concentrated) && select('concentrated', 'Concentrado', [{ value: 'true', label: 'Sim' }])}
      {(f.hasRefill || params.refill) && select('refill', 'Refil', [{ value: 'true', label: 'Sim' }])}
      {select('sort', 'Ordenar', [{ value: 'relevance', label: 'Relevância' },{ value: 'lowest', label: 'Menor preço' },{ value: 'highest', label: 'Maior preço' },{ value: 'newest', label: 'Novidades' }])}
      {params.section && <input type="hidden" name="section" value={params.section} />}
      <div className="catalog-filter-actions">
        <button className="catalog-button catalog-button-primary" type="submit" disabled={pending}>{pending && action === 'apply' ? 'Aplicando…' : 'Aplicar filtros'}</button>
        <Link className="catalog-button catalog-button-secondary" href="/produtos#catalog-results" aria-disabled={pending} onNavigate={(event) => { event.preventDefault(); navigate('/produtos', 'clear'); }}>{pending && action === 'clear' ? 'Limpando…' : 'Limpar filtros'}</Link>
      </div>
    </form></ResponsiveCatalogFilters>
    <div className="catalog-results" id="catalog-results" ref={resultsRef} tabIndex={-1}>
      <p role="status" aria-live="polite">{pending ? 'Carregando produtos…' : <>{result.totalElements} produto{result.totalElements === 1 ? '' : 's'} encontrado{result.totalElements === 1 ? '' : 's'}{result.totalElements > 0 && <span className="catalog-result-range">Exibindo {result.page * result.size + 1}–{result.page * result.size + result.content.length} · Página {result.page + 1} de {result.totalPages}</span>}</>}</p>
      {result.content.length ? <div className={`products-grid${pending ? ' catalog-products-loading' : ''}`}>{result.content.map((product) => <ProductCard key={product.id} product={product} returnTo={current} />)}</div> : <div className="empty-catalog"><h2>Nenhum produto encontrado</h2><p>Ajuste a busca ou remova alguns filtros.</p><Link className="catalog-button catalog-button-secondary" href="/produtos#catalog-results" onNavigate={(event) => { event.preventDefault(); navigate('/produtos', 'clear'); }}>Limpar filtros</Link></div>}
      {result.totalPages > 1 && <nav className="catalog-pagination" aria-label="Paginação de produtos">
        {result.page > 0 ? <Link className="catalog-page-direction" href={`${catalogHref(path, params, { page: String(result.page - 1) })}#catalog-results`} aria-disabled={pending} onNavigate={(event) => { event.preventDefault(); navigate(catalogHref(path, params, { page: String(result.page - 1) }), 'page'); }}>← Anterior</Link> : <span className="catalog-page-direction" aria-disabled="true">← Anterior</span>}
        <div className="catalog-page-numbers">{catalogPages(result.page, result.totalPages).map((page, index) => page === 'ellipsis' ? <span className="catalog-page-ellipsis" key={`ellipsis-${index}`} aria-hidden="true">…</span> : page === result.page ? <span className="catalog-page-current" aria-current="page" aria-label={`Página ${page + 1}`} key={page}>{page + 1}</span> : <Link key={page} aria-label={`Página ${page + 1}`} aria-disabled={pending} href={`${catalogHref(path, params, { page: String(page) })}#catalog-results`} onNavigate={(event) => { event.preventDefault(); navigate(catalogHref(path, params, { page: String(page) }), 'page'); }}>{page + 1}</Link>)}</div>
        {result.page + 1 < result.totalPages ? <Link className="catalog-page-direction" href={`${catalogHref(path, params, { page: String(result.page + 1) })}#catalog-results`} aria-disabled={pending} onNavigate={(event) => { event.preventDefault(); navigate(catalogHref(path, params, { page: String(result.page + 1) }), 'page'); }}>Próxima →</Link> : <span className="catalog-page-direction" aria-disabled="true">Próxima →</span>}
      </nav>}
    </div>
  </section>;
}
