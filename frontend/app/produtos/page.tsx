import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCatalog } from './ProductCatalog';
import { fetchProducts } from '@/lib/products';

export default async function ProductsPage({ searchParams }: { searchParams: Promise<{ busca?: string; categoria?: string }> }) {
  const { busca, categoria } = await searchParams;
  let products: Awaited<ReturnType<typeof fetchProducts>> = [];

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
        <p className="eyebrow">A curadoria inGarage</p>
        <h1 id="catalog-title">Produtos</h1>
        <p>Performance, proteção e cuidado para cada detalhe do seu carro.</p>
      </section>
      {apiError && <div className="api-error-banner">Não foi possível carregar produtos. Tente novamente mais tarde.</div>}
      <ProductCatalog products={products} initialSearch={busca} initialCategory={categoria} />
      <Footer />
    </main>
  );
}
