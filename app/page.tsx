import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ArrowUpRightIcon } from '@/components/Icons';
import { Product, ProductCard } from '@/components/ProductCard';

const products: Product[] = [
  { name: 'Shampoo Neutro Pro', category: 'Lavagem', price: 'R$ 49,90', accent: '#b5c4c3', image: 'https://images.unsplash.com/photo-1607860108855-64acf2078ed9?auto=format&fit=crop&w=900&q=85' },
  { name: 'Cera Sintética Ultra', category: 'Proteção', price: 'R$ 89,90', oldPrice: 'R$ 109,90', accent: '#c8b99d', image: 'https://images.unsplash.com/photo-1625047509248-ec889cbff17f?auto=format&fit=crop&w=900&q=85' },
  { name: 'Kit Pincéis Detalhamento', category: 'Detalhamento', price: 'R$ 74,90', accent: '#a9b0bc', image: 'https://images.unsplash.com/photo-1565689876697-52c4f4c8c1ad?auto=format&fit=crop&w=900&q=85' },
  { name: 'Toalha de Secagem 1200', category: 'Acessórios', price: 'R$ 39,90', accent: '#b8b8a2', image: 'https://images.unsplash.com/photo-1600706432502-77a0e2e3279c?auto=format&fit=crop&w=900&q=85' },
];

export default function Home() {
  return (
    <main id="top">
      <Header />
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-content">
          <p className="eyebrow">Cuidado que se sente na estrada</p>
          <h1 id="hero-title">Seu carro.<br /><em>Seu ritual.</em></h1>
          <p className="hero-copy">Produtos de alta performance para transformar o cuidado automotivo em parte do seu dia.</p>
          <a className="button button-light" href="#produtos">Explorar produtos <ArrowUpRightIcon /></a>
        </div>
        <div className="hero-meta"><span>01 / 03</span><span className="hero-line" /><span>Estética automotiva</span></div>
      </section>

      <section className="intro-band" id="kits">
        <p className="eyebrow">A curadoria GARAGE</p>
        <p className="intro-text">Tudo o que você precisa para manter seu carro <strong>com aparência de novo.</strong></p>
        <a className="text-link" href="#produtos">Conheça nossa seleção <ArrowUpRightIcon /></a>
      </section>

      <section className="products-section" id="produtos" aria-labelledby="products-title">
        <div className="section-heading"><div><p className="eyebrow">Escolhas da casa</p><h2 id="products-title">Produtos em destaque</h2></div><a className="text-link desktop-link" href="#todos-produtos">Ver todos <ArrowUpRightIcon /></a></div>
        <div className="products-grid">{products.map((product) => <ProductCard key={product.name} product={product} />)}</div>
        <a className="text-link mobile-link" href="#todos-produtos">Ver todos os produtos <ArrowUpRightIcon /></a>
      </section>
      <Footer />
    </main>
  );
}