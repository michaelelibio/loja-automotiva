import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCatalog } from './ProductCatalog';

export default function ProductsPage() {
  return (
    <main id="top">
      <Header />
      <section className="catalog-intro" aria-labelledby="catalog-title">
        <p className="eyebrow">A curadoria GARAGE</p>
        <h1 id="catalog-title">Produtos</h1>
        <p>Performance, proteção e cuidado para cada detalhe do seu carro.</p>
      </section>
      <ProductCatalog />
      <Footer />
    </main>
  );
}