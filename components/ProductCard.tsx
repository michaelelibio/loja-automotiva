'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, type MouseEvent } from 'react';
import { ArrowUpRightIcon, HeartIcon } from './Icons';
import { useAuth } from '@/context/AuthContext';
import { useFavorites } from '@/context/FavoritesContext';
import type { Product } from '@/lib/products';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

function hasValidImage(url: string) {
  return typeof url === 'string' && url.trim().length > 0 && /^https?:\/\//i.test(url);
}

export function ProductCard({ product }: { product: Product }) {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const { isFavorite, toggleFavorite } = useFavorites();
  const [isProcessing, setIsProcessing] = useState(false);
  const [failedImage, setFailedImage] = useState<string | null>(null);
  const favorite = isFavorite(product.id);
  const showImage = hasValidImage(product.image) && failedImage !== product.image;

  const handleFavoriteClick = async (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();

    if (!isAuthenticated) {
      router.push('/login');
      return;
    }

    setIsProcessing(true);
    try {
      await toggleFavorite(product);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <article className="product-card">
      <div className="product-image-wrapper">
        <Link
          href={`/produtos/${product.slug}`}
          className={`product-image ${showImage ? '' : 'placeholder'}`}
          style={{ backgroundColor: product.accent }}
          aria-label={`Ver detalhes de ${product.name}`}
        >
          {showImage && <img className="product-card-image" src={product.image} alt="" onError={() => setFailedImage(product.image)} />}
          <span className="product-labels">
            {product.productType === 'KIT' && <span className="product-type-badge">Kit</span>}
            <span className="product-category">{product.category}</span>
          </span>
          <span className="product-link" aria-hidden="true">
            <ArrowUpRightIcon />
          </span>
          {!showImage && <span className="product-image-fallback">Imagem indisponível</span>}
        </Link>

        <button
          type="button"
          className={`product-favorite ${favorite ? 'favorited' : ''}`}
          aria-pressed={favorite}
          onClick={handleFavoriteClick}
          disabled={isProcessing}
          title={favorite ? 'Remover dos favoritos' : 'Adicionar aos favoritos'}
        >
          <HeartIcon filled={favorite} />
        </button>
      </div>

      <div className="product-info">
        <Link href={`/produtos/${product.slug}`} className="product-card-title">
          <h3>{product.name}</h3>
        </Link>
        <div className="price-row">
          <strong>{currency.format(product.price)}</strong>
          {product.oldPrice && <span>{currency.format(product.oldPrice)}</span>}
        </div>
      </div>
    </article>
  );
}
