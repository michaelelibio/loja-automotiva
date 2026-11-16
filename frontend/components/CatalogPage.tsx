import Link from 'next/link';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { ProductCatalog } from '@/app/produtos/ProductCatalog';
import { catalogParams, catalogHref, fetchCatalog, resolveCategory } from '@/lib/catalog';
export async function CatalogPage({ raw, categoryPath }: { raw: Record<string, string | string[] | undefined>; categoryPath?: string }) {
  const params = catalogParams(raw);
  const path = categoryPath ? `/categorias/${categoryPath}` : '/produtos';
  let category = null; let parent = null; let result = null; let error = false;
  try {
    if (categoryPath) {
      category = await resolveCategory(categoryPath);
      params.category = categoryPath.split('/')[0];
      if (category.parentId !== null) { params.subcategory = categoryPath; parent = await resolveCategory(params.category); }
      else delete params.subcategory;
      delete params.legacyCategory;
    }
    result = await fetchCatalog(params);
  } catch { error = true; }
  return <main id="top"><Header /><section className="catalog-intro"><nav className="breadcrumbs" aria-label="Caminho"><Link href="/">Início</Link><span>/</span><Link href="/produtos">Produtos</Link>{parent && <><span>/</span><Link href={`/categorias/${parent.path}`}>{parent.name}</Link></>}{category && <><span>/</span><span aria-current="page">{category.name}</span></>}</nav><p className="eyebrow">A curadoria inGarage</p><h1>{category?.name ?? 'Produtos'}</h1><p>{category?.description ?? 'Encontre o cuidado para cada detalhe do seu carro.'}</p></section>
    {error && <div className="api-error-banner" role="alert"><p>Não foi possível carregar esta seleção. Confira os filtros e tente novamente.</p><Link href={catalogHref(path, params)}>Tentar novamente</Link> · <Link href="/produtos">Ver catálogo</Link></div>}
    {result && <ProductCatalog key={catalogHref(path, params)} result={result} params={params} path={path} />}<Footer /></main>;
}
