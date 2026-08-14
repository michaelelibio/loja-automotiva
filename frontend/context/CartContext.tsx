'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { Product } from '@/lib/products';

export type CartItem = {
  productId: string;
  quantity: number;
};

type CartContextValue = {
  items: CartItem[];
  totalItems: number;
  isHydrated: boolean;
  addItem: (product: Product, quantity: number) => void;
  increaseItem: (productId: string) => void;
  decreaseItem: (productId: string) => void;
  removeItem: (productId: string) => void;
  clearCart: () => void;
  reconcileItems: (validProductIds: string[]) => void;
};

const storageKey = 'garage-cart';
const CartContext = createContext<CartContextValue | null>(null);

function normalizeProductId(value: unknown): string | null {
  const id = typeof value === 'number' ? String(value) : typeof value === 'string' ? value.trim() : '';
  return /^\d+$/.test(id) && Number.isSafeInteger(Number(id)) && Number(id) > 0 ? String(Number(id)) : null;
}

function sanitizeCartItems(value: unknown): CartItem[] {
  if (!Array.isArray(value)) return [];
  const quantities = new Map<string, number>();
  value.forEach((candidate) => {
    if (!candidate || typeof candidate !== 'object') return;
    const item = candidate as Record<string, unknown>;
    const productId = normalizeProductId(item.productId);
    const quantity = typeof item.quantity === 'number' ? item.quantity : Number(item.quantity);
    if (!productId || !Number.isSafeInteger(quantity) || quantity <= 0) return;
    quantities.set(productId, (quantities.get(productId) ?? 0) + quantity);
  });
  return Array.from(quantities, ([productId, quantity]) => ({ productId, quantity }));
}

export function CartProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [items, setItems] = useState<CartItem[]>([]);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    const storedItems = window.localStorage.getItem(storageKey);
    if (storedItems) {
      try {
        setItems(sanitizeCartItems(JSON.parse(storedItems)));
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
    isHydrated: hydrated,
    totalItems: items.reduce((total, item) => total + item.quantity, 0),
    addItem: (product, quantity) => setItems((current) => {
      const productId = normalizeProductId(product.id);
      if (!productId || !Number.isSafeInteger(quantity) || quantity <= 0) return current;
      const existing = current.find((item) => item.productId === productId);
      if (existing) return current.map((item) => item.productId === productId ? { ...item, quantity: item.quantity + quantity } : item);
      return [...current, { productId, quantity }];
    }),
    increaseItem: (productId) => setItems((current) => current.map((item) => item.productId === productId ? { ...item, quantity: item.quantity + 1 } : item)),
    decreaseItem: (productId) => setItems((current) => current.flatMap((item) => item.productId === productId ? (item.quantity > 1 ? [{ ...item, quantity: item.quantity - 1 }] : []) : [item])),
    removeItem: (productId) => setItems((current) => current.filter((item) => item.productId !== productId)),
    clearCart: () => setItems([]),
    reconcileItems: (validProductIds) => {
      const validIds = new Set(validProductIds.map(normalizeProductId).filter((id): id is string => id !== null));
      setItems((current) => {
        const reconciled = current.filter((item) => validIds.has(item.productId));
        return reconciled.length === current.length ? current : reconciled;
      });
    },
  }), [items, hydrated]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside CartProvider');
  return context;
}
