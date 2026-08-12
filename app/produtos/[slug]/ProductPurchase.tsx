'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import type { Product } from '@/lib/products';
import { useCart } from '@/context/CartContext';

export function ProductPurchase({ product, available }: { product: Product; available: boolean }) {
  const [quantity, setQuantity] = useState(1);
  const [toastVisible, setToastVisible] = useState(false);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { addItem } = useCart();

  useEffect(() => () => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
  }, []);

  function handleAddToCart() {
    addItem(product, quantity);
    setToastVisible(true);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastVisible(false), 3000);
  }

  return (
    <div className="purchase-area">
      <div className="quantity-control" aria-label="Quantidade">
        <button type="button" onClick={() => setQuantity((current) => Math.max(1, current - 1))} aria-label="Diminuir quantidade">−</button>
        <span>{quantity}</span>
        <button type="button" onClick={() => setQuantity((current) => current + 1)} aria-label="Aumentar quantidade">+</button>
      </div>
      <button type="button" className="add-cart-button" disabled={!available} onClick={handleAddToCart}>Adicionar ao carrinho <span>↗</span></button>
      {toastVisible && <div className="cart-toast" role="status" aria-live="polite"><span>Produto adicionado ao carrinho ✓</span><Link href="/carrinho">Ver carrinho</Link></div>}
    </div>
  );
}