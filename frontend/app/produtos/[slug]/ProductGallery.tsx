'use client';

/* Product media comes from supplier CDNs with dynamic hosts; native img keeps runtime fallback handling. */
/* eslint-disable @next/next/no-img-element */

import { useMemo, useState } from 'react';
import type { Product } from '@/lib/products';

type GalleryImage = { id: string; url: string; alt: string };

function validImageUrl(value?: string): value is string {
  return typeof value === 'string' && /^https?:\/\//i.test(value.trim());
}

export function ProductGallery({ product, variantImageUrl, selectedUrl, onSelect }: {
  product: Product;
  variantImageUrl?: string;
  selectedUrl: string;
  onSelect: (url: string) => void;
}) {
  const images = useMemo<GalleryImage[]>(() => {
    const seen = new Set<string>();
    const result: GalleryImage[] = [];
    const add = (id: string, url: string | undefined, alt: string) => {
      if (!validImageUrl(url)) return;
      const normalized = url.trim();
      if (seen.has(normalized)) return;
      seen.add(normalized);
      result.push({ id, url: normalized, alt });
    };

    product.media?.filter((media) => media.type === 'IMAGE')
      .forEach((media, index) => add(media.id, media.url,
        media.altText || `${product.name} — imagem ${index + 1}`));
    if (result.length === 0) product.images.forEach((url, index) =>
      add(`legacy-${index}`, url, `${product.name} — imagem ${index + 1}`));
    if (result.length === 0) add('fallback', product.image, product.name);
    add('selected-variant', variantImageUrl, `${product.name} — variante selecionada`);
    return result;
  }, [product, variantImageUrl]);

  const [failedUrls, setFailedUrls] = useState<Set<string>>(() => new Set());
  const selectedIndex = Math.max(0, images.findIndex((image) => image.url === selectedUrl));
  const selected = images[selectedIndex];
  const showImage = selected && !failedUrls.has(selected.url);

  const markFailed = (url: string) => setFailedUrls((current) => {
    const next = new Set(current);
    next.add(url);
    return next;
  });

  return (
    <div className="product-gallery">
      <div className="product-main-image" style={{ backgroundColor: product.accent }}>
        {showImage ? <img src={selected.url} alt={selected.alt} onError={() => markFailed(selected.url)} />
          : <span className="product-gallery-fallback">Imagem indisponível</span>}
        {images.length > 1 && <span className="product-gallery-count" aria-live="polite">
          {selectedIndex + 1} / {images.length}
        </span>}
      </div>
      {images.length > 1 && <div className="product-thumbnails" aria-label="Galeria do produto">
        {images.map((image, index) => <button key={`${image.id}-${image.url}`} type="button"
          className={`product-thumbnail ${image.url === selected?.url ? 'active' : ''}`}
          onClick={() => onSelect(image.url)}
          aria-label={`Mostrar imagem ${index + 1} de ${product.name}`}
          aria-pressed={image.url === selected?.url}>
          {!failedUrls.has(image.url) ? <img src={image.url} alt="" onError={() => markFailed(image.url)} />
            : <span aria-hidden="true">—</span>}
        </button>)}
      </div>}
    </div>
  );
}
