'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { Product } from '@/lib/products';
import { ProductGallery } from './ProductGallery';
import { ProductPurchase } from './ProductPurchase';
import { variantOptions } from '@/lib/product-options';

export function ProductDetailClient({ product }: { product: Product }) {
  const [selectedVariantId, setSelectedVariantId] = useState(
    () => variantOptions(product.variants).automaticId);
  const [selectedMediaUrl, setSelectedMediaUrl] = useState(
    () => product.variants.find(v => v.id === variantOptions(product.variants).automaticId)?.imageUrl ?? '');
  const selectedVariant = product.variants.find((variant) => variant.id === selectedVariantId);
  const available = product.availableForSale && (!product.requiresVariantSelection || variantOptions(product.variants).available.length>0)
    && (!selectedVariant || selectedVariant.availableForSale !== false);
  const stock = selectedVariant?.stockQuantity ?? (product.fulfillmentType === 'LOCAL_STOCK' ? product.stock : null);

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
      <div className="detail-price"><strong>{formatPrice(selectedVariant?.price ?? product.price)}</strong>
        {product.oldPrice && <span>{formatPrice(product.oldPrice)}</span>}</div>
      <p className={`stock-status ${available ? 'available' : 'unavailable'}`} aria-live="polite"><span />
        {available ? stock == null ? 'Disponível' : `Em estoque · ${stock} unidades disponíveis` : 'Produto indisponível'}</p>
      <ProductPurchase product={product} available={available}
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
