'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { Product } from '@/lib/products';
import { ProductGallery } from './ProductGallery';
import { ProductPurchase } from './ProductPurchase';

export function ProductDetailClient({ product }: { product: Product }) {
  const [selectedVariantId, setSelectedVariantId] = useState(
    () => product.variants.length === 1 ? product.variants[0].id : '');
  const [selectedMediaUrl, setSelectedMediaUrl] = useState(
    () => product.variants.length === 1 ? product.variants[0].imageUrl ?? '' : '');
  const selectedVariant = product.variants.find((variant) => variant.id === selectedVariantId);

  const handleVariantChange = (variantId: string) => {
    setSelectedVariantId(variantId);
    const imageUrl = product.variants.find((variant) => variant.id === variantId)?.imageUrl;
    if (imageUrl) setSelectedMediaUrl(imageUrl);
  };

  return <section className="product-detail" aria-labelledby="product-title">
    <ProductGallery product={product} variantImageUrl={selectedVariant?.imageUrl}
      selectedUrl={selectedMediaUrl} onSelect={setSelectedMediaUrl} />
    <div className="product-detail-info">
      <Link className="product-back-link" href="/produtos">← Voltar para produtos</Link>
      <div className="product-detail-labels">
        {product.productType === 'KIT' && <span className="product-detail-type">Kit</span>}
        <p className="eyebrow">{product.category}</p>
      </div>
      <h1 id="product-title">{product.name}</h1>
      <p className="product-description">{product.description}</p>
      <div className="detail-price"><strong>{formatPrice(product.price)}</strong>
        {product.oldPrice && <span>{formatPrice(product.oldPrice)}</span>}</div>
      <p className={`stock-status ${product.availableForSale ? 'available' : 'unavailable'}`}><span />
        {product.availableForSale ? product.fulfillmentType === 'DROPSHIPPING' ? 'Disponível'
          : `Em estoque · ${product.stock} unidades disponíveis` : 'Produto indisponível'}</p>
      <ProductPurchase product={product} available={product.availableForSale}
        selectedVariantId={selectedVariantId} onVariantChange={handleVariantChange} />
      <div className="detail-description"><p>{product.longDescription}</p></div>
      {product.features.length > 0 && <div className="features"><h2>Principais características</h2>
        <ul>{product.features.map((feature) => <li key={feature}>{feature}</li>)}</ul></div>}
    </div>
  </section>;
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}
