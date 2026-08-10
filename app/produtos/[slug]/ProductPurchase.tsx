'use client';

import { useState } from 'react';

export function ProductPurchase({ available }: { available: boolean }) {
  const [quantity, setQuantity] = useState(1);

  return (
    <div className="purchase-area">
      <div className="quantity-control" aria-label="Quantidade">
        <button type="button" onClick={() => setQuantity((current) => Math.max(1, current - 1))} aria-label="Diminuir quantidade">−</button>
        <span>{quantity}</span>
        <button type="button" onClick={() => setQuantity((current) => current + 1)} aria-label="Aumentar quantidade">+</button>
      </div>
      <button type="button" className="add-cart-button" disabled={!available}>Adicionar ao carrinho <span>↗</span></button>
    </div>
  );
}