'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { Product } from '@/lib/products';
import { useCart } from '@/context/CartContext';
import { ProductVariantSelector } from './ProductVariantSelector';

export function ProductPurchase({ product, available, selectedVariantId, onVariantChange }: {
  product: Product;
  available: boolean;
  selectedVariantId: string;
  onVariantChange: (variantId: string) => void;
}) {
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const { addItem } = useCart();
  const maximumLocalQuantity = product.fulfillmentType === 'LOCAL_STOCK' ? Math.max(0, product.stock) : null;
  const canAdd = available && (!product.requiresVariantSelection || selectedVariantId !== '');

  function handleAddToCart() {
    if (!canAdd) return;
    addItem(product, quantity, selectedVariantId || undefined);
    setAdded(true);
  }

  return (
    <div className="purchase-block">
      {product.requiresVariantSelection && <ProductVariantSelector variants={product.variants}
        available={available} selectedVariantId={selectedVariantId}
        onChange={(variantId) => { onVariantChange(variantId); setAdded(false); }} />}
      <div className="purchase-area">
        <div className="quantity-control" aria-label="Quantidade">
          <button type="button" disabled={quantity <= 1 || !available}
            onClick={() => setQuantity((current) => Math.max(1, current - 1))}
            aria-label="Diminuir quantidade">−</button>
          <span>{quantity}</span>
          <button type="button" disabled={!available
            || (maximumLocalQuantity !== null && quantity >= maximumLocalQuantity)}
            onClick={() => setQuantity((current) => maximumLocalQuantity === null
              ? current + 1 : Math.min(maximumLocalQuantity, current + 1))}
            aria-label="Aumentar quantidade">+</button>
        </div>
        <button type="button" className="add-cart-button" disabled={!canAdd} onClick={handleAddToCart}>
          Adicionar ao carrinho <span>↗</span>
        </button>
      </div>
      {added && <div className="purchase-feedback" role="status" aria-live="polite">
        <p><span aria-hidden="true">✓</span> Produto adicionado ao carrinho.</p>
        <div><Link href="/produtos">Continuar comprando <span aria-hidden="true">→</span></Link>
          <Link href="/carrinho">Ver carrinho <span aria-hidden="true">→</span></Link></div>
      </div>}
    </div>
  );
}
