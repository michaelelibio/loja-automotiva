import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ArrowUpRightIcon } from '@/components/Icons';
import { ProductCard } from '@/components/ProductCard';
import { fetchProducts } from '@/lib/products';
import { products as fallbackProducts } from '@/data/products';

export default async function Home() {
  let products = fallbackProducts;

  let apiError = false;

  try {
    products = await fetchProducts();
  } catch {
    apiError = true;
  }

  return (
    <main id="top">
      <Header />
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-content">
          <p className="eyebrow">Cuidado que se sente na estrada</p>
          <h1 id="hero-title">Seu carro.<br /><em>Seu ritual.</em></h1>
          <p className="hero-copy">Produtos de alta performance para transformar o cuidado automotivo em parte do seu dia.</p>
          <a className="button button-light" href="/produtos">Explorar produtos <ArrowUpRightIcon /></a>
        </div>
        <div className="hero-meta"><span>01 / 03</span><span className="hero-line" /><span>Estética automotiva</span></div>
      </section>

      <section className="intro-band" id="kits">
        <p className="eyebrow">A curadoria GARAGE</p>
        <p className="intro-text">Tudo o que você precisa para manter seu carro <strong>com aparência de novo.</strong></p>
        <a className="text-link" href="/produtos">Conheça nossa seleção <ArrowUpRightIcon /></a>
      </section>

      <section className="products-section" id="produtos" aria-labelledby="products-title">
        <div className="section-heading"><div><p className="eyebrow">Escolhas da casa</p><h2 id="products-title">Produtos em destaque</h2></div><a className="text-link desktop-link" href="/produtos">Ver todos <ArrowUpRightIcon /></a></div>
        {apiError && <div className="api-error-banner">Não foi possível carregar produtos da API. Exibindo dados locais em modo de fallback.</div>}
        {products.length > 0 ? (
          <div className="products-grid">{products.filter((product) => product.featured).length > 0 ? products.filter((product) => product.featured).map((product) => <ProductCard key={product.id} product={product} />) : products.map((product) => <ProductCard key={product.id} product={product} />)}</div>
        ) : (
          <div className="empty-catalog"><h2>Nenhum produto disponível</h2><p>Estamos sem produtos no momento. Volte em breve.</p></div>
        )}
        <a className="text-link mobile-link" href="/produtos">Ver todos os produtos <ArrowUpRightIcon /></a>
      </section>
      <Footer />
    </main>
  );
}