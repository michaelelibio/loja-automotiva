import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCatalog } from './ProductCatalog';
import { fetchProducts } from '@/lib/products';
import { products as fallbackProducts } from '@/data/products';

export default async function ProductsPage() {
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
      <section className="catalog-intro" aria-labelledby="catalog-title">
        <p className="eyebrow">A curadoria GARAGE</p>
        <h1 id="catalog-title">Produtos</h1>
        <p>Performance, proteção e cuidado para cada detalhe do seu carro.</p>
      </section>
      {apiError && <div className="api-error-banner">Não foi possível carregar produtos da API. Exibindo dados locais em modo de fallback.</div>}
      <ProductCatalog products={products} />
      <Footer />
    </main>
  );
}