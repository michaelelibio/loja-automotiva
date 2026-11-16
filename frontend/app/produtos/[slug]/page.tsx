import { notFound } from 'next/navigation';
import Link from 'next/link';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCard } from '@/components/ProductCard';
import { fetchProductBySlug, fetchProducts } from '@/lib/products';
import { ProductDetailClient } from './ProductDetailClient';
import { catalogReturnTo, fetchCatalog } from '@/lib/catalog';
import { catalogLabels } from '@/lib/catalog-types';

type ProductPageProps = {
  params: Promise<{ slug: string }>;
  searchParams?: Promise<{ voltar?: string }>;
};

export default async function ProductPage({ params, searchParams }: ProductPageProps) {
  const { slug } = await params;
  const product = await fetchProductBySlug(slug).catch(() => null);
  if (!product) notFound();

  const back = catalogReturnTo((await searchParams)?.voltar);
  const relatedProductsSource = product.catalog?.category ? await fetchCatalog({ category: product.catalog.category.path, size: '5' }).then((r) => r.content).catch(() => []) : await fetchProducts().catch(() => []);
  const relatedProducts = relatedProductsSource
    .filter((item) => (product.catalog?.category || item.category === product.category) && item.id !== product.id)
    .slice(0, 4);

  return (
    <main id="top">
      <Header />
      <div className="product-detail-page">
        <nav className="product-breadcrumb" aria-label="Breadcrumb">
          <Link href="/">Início</Link><span>/</span><Link href={back}>Produtos</Link>
          {product.catalog?.category && <><span>/</span><Link href={`/categorias/${product.catalog.category.path}`}>{product.catalog.category.name}</Link></>}
          {product.catalog?.subcategory && <><span>/</span><Link href={`/categorias/${product.catalog.subcategory.path}`}>{product.catalog.subcategory.name}</Link></>}
          <span>/</span><strong>{product.name}</strong>
        </nav>
        <ProductDetailClient product={product} />
        {product.catalog && <dl className="catalog-product-metadata">
          {product.catalog.brand && <div><dt>Marca</dt><dd>{product.catalog.brand}</dd></div>}
          {product.catalog.surfaces.length > 0 && <div><dt>Superfícies</dt><dd>{product.catalog.surfaces.join(', ')}</dd></div>}
          {product.catalog.purposes.length > 0 && <div><dt>Finalidades</dt><dd>{product.catalog.purposes.join(', ')}</dd></div>}
          {product.catalog.usage && <div><dt>Uso</dt><dd>{catalogLabels[product.catalog.usage]}</dd></div>}
          {product.catalog.concentrated != null && <div><dt>Concentrado</dt><dd>{product.catalog.concentrated ? 'Sim' : 'Não'}</dd></div>}
          {product.catalog.refill != null && <div><dt>Refil</dt><dd>{product.catalog.refill ? 'Sim' : 'Não'}</dd></div>}
          {product.catalog.commercialOrigin && <div><dt>Seleção comercial</dt><dd>{catalogLabels[product.catalog.commercialOrigin]}</dd></div>}
        </dl>}
        {relatedProducts.length > 0 && <section className="related-products" aria-labelledby="related-title">
          <div className="section-heading"><div><p className="eyebrow">Para completar o ritual</p>
            <h2 id="related-title">Você também pode gostar</h2></div></div>
          <div className="products-grid">{relatedProducts.map((item) =>
            <ProductCard key={item.id} product={item} />)}</div>
        </section>}
      </div>
      <Footer />
    </main>
  );
}
