import { notFound } from 'next/navigation';
import Link from 'next/link';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCard } from '@/components/ProductCard';
import { products } from '@/data/products';
import { ProductPurchase } from './ProductPurchase';

type ProductPageProps = {
  params: Promise<{ slug: string }>;
};

export function generateStaticParams() {
  return products.map((product) => ({ slug: product.slug }));
}

export default async function ProductPage({ params }: ProductPageProps) {
  const { slug } = await params;
  const product = products.find((item) => item.slug === slug);

  if (!product) notFound();

  const relatedProducts = products
    .filter((item) => item.category === product.category && item.id !== product.id)
    .slice(0, 4);

  return (
    <main id="top">
      <Header />
      <div className="product-detail-page">
        <nav className="product-breadcrumb" aria-label="Breadcrumb">
          <Link href="/">Início</Link><span>/</span><Link href="/produtos">Produtos</Link><span>/</span><strong>{product.name}</strong>
        </nav>

        <section className="product-detail" aria-labelledby="product-title">
          <div className="product-gallery">
            <div className="product-main-image" style={{ backgroundImage: `url(${product.images[0]})`, backgroundColor: product.accent }} aria-label={`Imagem de ${product.name}`} role="img" />
            <div className="product-thumbnails">
              {product.images.map((image, index) => <span key={image} className={`product-thumbnail ${index === 0 ? 'active' : ''}`} style={{ backgroundImage: `url(${image})`, backgroundColor: product.accent }} aria-label={`Imagem ${index + 1} de ${product.name}`} />)}
            </div>
          </div>

          <div className="product-detail-info">
            <p className="eyebrow">{product.category}</p>
            <h1 id="product-title">{product.name}</h1>
            <p className="product-description">{product.description}</p>
            <div className="detail-price"><strong>{formatPrice(product.price)}</strong>{product.oldPrice && <span>{formatPrice(product.oldPrice)}</span>}</div>
            <p className={`stock-status ${product.stock > 0 ? 'available' : 'unavailable'}`}><span />{product.stock > 0 ? `Em estoque · ${product.stock} unidades disponíveis` : 'Produto indisponível'}</p>
            <ProductPurchase product={product} available={product.stock > 0} />
            <div className="detail-description"><p>{product.longDescription}</p></div>
            <div className="features"><h2>Principais características</h2><ul>{product.features.map((feature) => <li key={feature}>{feature}</li>)}</ul></div>
          </div>
        </section>

        {relatedProducts.length > 0 && <section className="related-products" aria-labelledby="related-title"><div className="section-heading"><div><p className="eyebrow">Para completar o ritual</p><h2 id="related-title">Você também pode gostar</h2></div></div><div className="products-grid">{relatedProducts.map((item) => <ProductCard key={item.id} product={item} />)}</div></section>}
      </div>
      <Footer />
    </main>
  );
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}