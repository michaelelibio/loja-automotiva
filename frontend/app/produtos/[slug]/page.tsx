import { notFound } from 'next/navigation';
import Link from 'next/link';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCard } from '@/components/ProductCard';
import { fetchProductBySlug, fetchProducts } from '@/lib/products';
import { ProductDetailClient } from './ProductDetailClient';

type ProductPageProps = {
  params: Promise<{ slug: string }>;
};

export default async function ProductPage({ params }: ProductPageProps) {
  const { slug } = await params;
  const product = await fetchProductBySlug(slug).catch(() => null);
  if (!product) notFound();

  const relatedProductsSource = await fetchProducts().catch(() => []);
  const relatedProducts = relatedProductsSource
    .filter((item) => item.category === product.category && item.id !== product.id)
    .slice(0, 4);

  return (
    <main id="top">
      <Header />
      <div className="product-detail-page">
        <nav className="product-breadcrumb" aria-label="Breadcrumb">
          <Link href="/">Início</Link><span>/</span><Link href="/produtos">Produtos</Link>
          <span>/</span><strong>{product.name}</strong>
        </nav>
        <ProductDetailClient product={product} />
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
