'use client';

import Link from 'next/link';
import { useState } from 'react';
import type { Product } from '@/lib/products';
import { useCart } from '@/context/CartContext';
import { ProductVariantSelector } from './ProductVariantSelector';
import { PurchaseDialog } from '@/components/PurchaseDialog';
import { variantLabel, variantOptions } from '@/lib/product-options';

export function ProductPurchase({ product, available, selectedVariantId, onVariantChange }: {
  product: Product;
  available: boolean;
  selectedVariantId: string;
  onVariantChange: (variantId: string) => void;
}) {
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const { addItem, items = [], isHydrated = true } = useCart();
  const effectiveId = selectedVariantId || variantOptions(product.variants).automaticId;
  const selectedVariant = product.variants.find(v => v.id === effectiveId);
  const maximumLocalQuantity = selectedVariant?.stockQuantity ?? (product.fulfillmentType === 'LOCAL_STOCK' ? Math.max(0, product.stock) : null);
  const alreadyInCart=items.find(item=>item.productId===product.id&&item.variantId===(effectiveId||undefined))?.quantity??0;
  const remaining=maximumLocalQuantity==null?null:Math.max(0,maximumLocalQuantity-alreadyInCart);
  const canAdd = isHydrated && available && (!product.requiresVariantSelection || !!selectedVariant && selectedVariant.availableForSale !== false)
    && (remaining == null || quantity <= remaining);

  function handleAddToCart() {
    if (!canAdd) return;
    addItem(product, quantity, effectiveId || undefined);
    setAdded(true);
  }

  return (
    <div className="purchase-block">
      {product.requiresVariantSelection && <ProductVariantSelector variants={product.variants}
        available={available} selectedVariantId={effectiveId}
        onChange={(variantId) => { onVariantChange(variantId); setQuantity(1); setAdded(false); }} />}
      <div className="purchase-area">
        <div className="quantity-control" aria-label="Quantidade">
          <button type="button" disabled={quantity <= 1 || !available}
            onClick={() => setQuantity((current) => Math.max(1, current - 1))}
            aria-label="Diminuir quantidade">−</button>
          <span>{quantity}</span>
          <button type="button" disabled={!available
            || (remaining !== null && quantity >= remaining)}
            onClick={() => setQuantity((current) => remaining === null
              ? current + 1 : Math.min(remaining, current + 1))}
            aria-label="Aumentar quantidade">+</button>
        </div>
        <button type="button" className="add-cart-button" disabled={!canAdd} onClick={handleAddToCart}>
          {isHydrated?'Adicionar ao carrinho':'Preparando carrinho...'} <span>↗</span>
        </button>
      </div>
      {remaining===0&&alreadyInCart>0&&<p role="status">A quantidade disponível já está no seu carrinho. <Link href="/carrinho">Ver carrinho</Link></p>}
      {added && <PurchaseDialog title="Produto adicionado com sucesso" onClose={()=>setAdded(false)}>
        <p><strong>{product.name}</strong></p><p>{variantLabel(selectedVariant)}</p><p>Quantidade: {quantity}</p>
        <div className="purchase-dialog-actions"><button type="button" className="purchase-primary" onClick={()=>setAdded(false)}>Continuar comprando</button>
          <Link className="purchase-primary" href="/carrinho">Ver carrinho</Link></div>
      </PurchaseDialog>}
    </div>
  );
}
