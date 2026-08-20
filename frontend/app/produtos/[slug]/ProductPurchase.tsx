'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { Product } from '@/lib/products';
import { useCart } from '@/context/CartContext';

export function ProductPurchase({ product, available }: { product: Product; available: boolean }) {
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const [selectedVariantId, setSelectedVariantId] = useState('');
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
      {product.requiresVariantSelection && <label className="variant-selector">
        <span>Escolha uma opção</span>
        <select value={selectedVariantId} onChange={(event) => { setSelectedVariantId(event.target.value); setAdded(false); }} disabled={!available}>
          <option value="">Selecione</option>
          {product.variants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}
        </select>
      </label>}
      <div className="purchase-area">
        <div className="quantity-control" aria-label="Quantidade">
          <button type="button" disabled={quantity <= 1 || !available} onClick={() => setQuantity((current) => Math.max(1, current - 1))} aria-label="Diminuir quantidade">−</button>
          <span>{quantity}</span>
          <button type="button" disabled={!available || (maximumLocalQuantity !== null && quantity >= maximumLocalQuantity)} onClick={() => setQuantity((current) => maximumLocalQuantity === null ? current + 1 : Math.min(maximumLocalQuantity, current + 1))} aria-label="Aumentar quantidade">+</button>
        </div>
        <button type="button" className="add-cart-button" disabled={!canAdd} onClick={handleAddToCart}>Adicionar ao carrinho <span>↗</span></button>
      </div>
      {added && <div className="purchase-feedback" role="status" aria-live="polite">
        <p><span aria-hidden="true">✓</span> Produto adicionado ao carrinho.</p>
        <div><Link href="/produtos">Continuar comprando <span aria-hidden="true">→</span></Link><Link href="/carrinho">Ver carrinho <span aria-hidden="true">→</span></Link></div>
      </div>}
    </div>
  );
}
