'use client';

import Link from 'next/link';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { useAuth } from '@/context/AuthContext';
import { useFavorites } from '@/context/FavoritesContext';
import { ProductCard } from '@/components/ProductCard';

export default function FavoritosPage() {
  const { isAuthenticated, isLoading, sessionError } = useAuth();
  const { favorites, isLoading: loadingFavorites, error } = useFavorites();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !sessionError && !isAuthenticated) {
      router.push('/login');
    }
  }, [isAuthenticated, isLoading, sessionError, router]);

  if (isLoading || sessionError || !isAuthenticated) {
    return (
      <main>
        <Header />
        <div style={{ padding: 80, textAlign: 'center' }}>{sessionError ?? 'Carregando favoritos...'}</div>
        <Footer />
      </main>
    );
  }

  return (
    <main id="top">
      <Header />
      <section className="catalog-intro" aria-labelledby="favorites-title">
        <p className="eyebrow">Favoritos</p>
        <h1 id="favorites-title">Meus produtos salvos</h1>
        <p>Confira os itens que você marcou para acessar depois.</p>
      </section>

      <div className="favorites-section">
        {loadingFavorites && <div className="api-error-banner">Carregando seus favoritos...</div>}
        {error && <div className="api-error-banner">{error}</div>}
        {!loadingFavorites && favorites.length === 0 && (
          <div className="empty-catalog">
            <h2>Você ainda não salvou nenhum favorito</h2>
            <p>Explore a loja e adicione produtos que você quer ver de novo.</p>
            <Link className="text-link" href="/produtos">Ver produtos</Link>
          </div>
        )}

        {favorites.length > 0 && (
          <div className="products-grid catalog-grid">
            {favorites.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </div>
      <Footer />
    </main>
  );
}
