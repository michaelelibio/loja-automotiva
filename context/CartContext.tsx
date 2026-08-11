'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { Product } from '@/data/products';

export type CartItem = {
  productId: string;
  quantity: number;
};

type CartContextValue = {
  items: CartItem[];
  totalItems: number;
  addItem: (product: Product, quantity: number) => void;
  increaseItem: (productId: string) => void;
  decreaseItem: (productId: string) => void;
  removeItem: (productId: string) => void;
};

const storageKey = 'garage-cart';
const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [items, setItems] = useState<CartItem[]>([]);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    const storedItems = window.localStorage.getItem(storageKey);
    if (storedItems) {
      try {
        const parsedItems = JSON.parse(storedItems) as CartItem[];
        if (Array.isArray(parsedItems)) setItems(parsedItems.filter((item) => item.productId && item.quantity > 0));
      } catch {
        window.localStorage.removeItem(storageKey);
      }
    }
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    window.localStorage.setItem(storageKey, JSON.stringify(items));
  }, [hydrated, items]);

  const value = useMemo<CartContextValue>(() => ({
    items,
    totalItems: items.reduce((total, item) => total + item.quantity, 0),
    addItem: (product, quantity) => setItems((current) => {
      const existing = current.find((item) => item.productId === product.id);
      if (existing) return current.map((item) => item.productId === product.id ? { ...item, quantity: item.quantity + quantity } : item);
      return [...current, { productId: product.id, quantity }];
    }),
    increaseItem: (productId) => setItems((current) => current.map((item) => item.productId === productId ? { ...item, quantity: item.quantity + 1 } : item)),
    decreaseItem: (productId) => setItems((current) => current.flatMap((item) => item.productId === productId ? (item.quantity > 1 ? [{ ...item, quantity: item.quantity - 1 }] : []) : [item])),
    removeItem: (productId) => setItems((current) => current.filter((item) => item.productId !== productId)),
  }), [items]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside CartProvider');
  return context;
}