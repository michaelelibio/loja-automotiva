'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { useCart } from '@/context/CartContext';
import { useProducts } from '@/lib/useProducts';
import { products as fallbackProducts } from '@/data/products';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function CartView() {
  const { items, increaseItem, decreaseItem, removeItem } = useCart();
  const { products, error } = useProducts();
  const [pendingRemovalId, setPendingRemovalId] = useState<string | null>(null);
  const availableProducts = error ? fallbackProducts : products.length > 0 ? products : fallbackProducts;
  const cartProducts = items.flatMap((item) => {
    const product = availableProducts.find((candidate) => candidate.id === item.productId);
    return product ? [{ ...item, product }] : [];
  });
  const total = cartProducts.reduce((sum, item) => sum + item.product.price * item.quantity, 0);

  if (cartProducts.length === 0) {
    return <section className="cart-section empty-cart"><p className="eyebrow">Nada por aqui ainda</p><h2>Seu carrinho está vazio</h2><Link className="button button-light" href="/produtos">Continuar comprando <span>↗</span></Link></section>;
  }

  return (
    <section className="cart-section" aria-label="Itens do carrinho">
      <div className="cart-items">
        {cartProducts.map(({ product, quantity }) => (
          <article className="cart-item" key={product.id}>
            <div className="cart-item-image" style={{ backgroundImage: `url(${product.image})`, backgroundColor: product.accent }} />
            <div className="cart-item-info"><p className="eyebrow">{product.category}</p><h2>{product.name}</h2><span>{currency.format(product.price)} / unidade</span></div>
            <div className="cart-item-quantity"><span>Quantidade</span><div className="quantity-control"><button type="button" onClick={() => decreaseItem(product.id)} aria-label={`Diminuir ${product.name}`}>−</button><strong>{quantity}</strong><button type="button" onClick={() => increaseItem(product.id)} aria-label={`Aumentar ${product.name}`}>+</button></div></div>
            <div className="cart-item-subtotal"><span>Subtotal</span><strong>{currency.format(product.price * quantity)}</strong><button type="button" className="remove-item" onClick={() => setPendingRemovalId(product.id)}>Remover</button></div>
            {pendingRemovalId === product.id && <RemoveConfirmation productName={product.name} onCancel={() => setPendingRemovalId(null)} onConfirm={() => { removeItem(product.id); setPendingRemovalId(null); }} />}
          </article>
        ))}
      </div>
      <aside className="cart-summary"><div><span>Subtotal geral</span><strong>{currency.format(total)}</strong></div><Link className="add-cart-button" href="/checkout">Finalizar compra <span>↗</span></Link><Link className="text-link" href="/produtos">Continuar comprando <span>↗</span></Link></aside>
    </section>
  );
}

function RemoveConfirmation({ productName, onCancel, onConfirm }: { productName: string; onCancel: () => void; onConfirm: () => void }) {
  const cancelButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cancelButton.current?.focus();
  }, []);

  return (
    <div className="remove-confirmation" role="alertdialog" aria-modal="true" aria-labelledby="remove-title">
      <p id="remove-title">Remover este produto do carrinho?</p>
      <span>{productName}</span>
      <div><button type="button" className="confirmation-cancel" ref={cancelButton} onClick={onCancel}>Cancelar</button><button type="button" className="confirmation-remove" onClick={onConfirm}>Remover</button></div>
    </div>
  );
}