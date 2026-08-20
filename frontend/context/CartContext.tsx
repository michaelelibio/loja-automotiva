'use client';

import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import type { Product } from '@/lib/products';

export type CartItem = {
  productId: string;
  variantId?: string;
  quantity: number;
};

type CartContextValue = {
  items: CartItem[];
  totalItems: number;
  isHydrated: boolean;
  addItem: (product: Product, quantity: number, variantId?: string) => void;
  increaseItem: (productId: string, variantId?: string) => void;
  decreaseItem: (productId: string, variantId?: string) => void;
  removeItem: (productId: string, variantId?: string) => void;
  clearCart: () => void;
  reconcileItems: (validProductIds: string[]) => void;
};

const storageKey = 'garage-cart';
const CartContext = createContext<CartContextValue | null>(null);

type StoredCart = {
  version: 2;
  activeUserId: number | null;
  guest: { ownerUserId: number | null; items: CartItem[] };
  users: Record<string, CartItem[]>;
};

const emptyStorage = (): StoredCart => ({ version: 2, activeUserId: null, guest: { ownerUserId: null, items: [] }, users: {} });

function normalizeProductId(value: unknown): string | null {
  const id = typeof value === 'number' ? String(value) : typeof value === 'string' ? value.trim() : '';
  return /^\d+$/.test(id) && Number.isSafeInteger(Number(id)) && Number(id) > 0 ? String(Number(id)) : null;
}

const itemKey = (productId: string, variantId?: string) => `${productId}:${variantId ?? ''}`;

function sanitizeCartItems(value: unknown): CartItem[] {
  if (!Array.isArray(value)) return [];
  const quantities = new Map<string, CartItem>();
  value.forEach((candidate) => {
    if (!candidate || typeof candidate !== 'object') return;
    const item = candidate as Record<string, unknown>;
    const productId = normalizeProductId(item.productId);
    const variantId = item.variantId == null ? undefined : normalizeProductId(item.variantId) ?? undefined;
    const quantity = typeof item.quantity === 'number' ? item.quantity : Number(item.quantity);
    if (!productId || !Number.isSafeInteger(quantity) || quantity <= 0) return;
    const key = itemKey(productId, variantId);
    const current = quantities.get(key);
    quantities.set(key, { productId, variantId, quantity: (current?.quantity ?? 0) + quantity });
  });
  return Array.from(quantities.values());
}

function readCartStorage(): StoredCart {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return emptyStorage();
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return { ...emptyStorage(), guest: { ownerUserId: null, items: sanitizeCartItems(parsed) } };
    }
    if (!parsed || typeof parsed !== 'object') return emptyStorage();
    const object = parsed as Record<string, unknown>;
    const guestObject = object.guest && typeof object.guest === 'object'
      ? object.guest as Record<string, unknown> : {};
    const owner = Number(guestObject.ownerUserId);
    const activeOwner = Number(object.activeUserId);
    const users: Record<string, CartItem[]> = {};
    if (object.users && typeof object.users === 'object') {
      Object.entries(object.users as Record<string, unknown>).forEach(([key, value]) => {
        if (/^\d+$/.test(key) && Number(key) > 0) users[String(Number(key))] = sanitizeCartItems(value);
      });
    }
    return {
      version: 2,
      activeUserId: Number.isSafeInteger(activeOwner) && activeOwner > 0 ? activeOwner : null,
      guest: {
        ownerUserId: Number.isSafeInteger(owner) && owner > 0 ? owner : null,
        items: sanitizeCartItems(guestObject.items),
      },
      users,
    };
  } catch {
    window.localStorage.removeItem(storageKey);
    return emptyStorage();
  }
}

function writeCartStorage(storage: StoredCart) {
  try { window.localStorage.setItem(storageKey, JSON.stringify(storage)); }
  catch { /* O carrinho continua disponível em memória quando o storage falha. */ }
}

export function CartProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { user, isLoading: authLoading } = useAuth();
  const [items, setItems] = useState<CartItem[]>([]);
  const [hydrated, setHydrated] = useState(false);
  const itemsRef = useRef<CartItem[]>([]);
  const activeUserRef = useRef<number | null | undefined>(undefined);

  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  useEffect(() => {
    if (authLoading) return;
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      const currentUserId = user?.id ?? null;
      const previousUserId = activeUserRef.current;
      if (previousUserId === currentUserId) return;

      const storage = readCartStorage();
      const currentItems = itemsRef.current;
      let selectedItems: CartItem[];

      if (previousUserId === undefined) {
        if (currentUserId === null) {
          if (storage.activeUserId !== null && Object.hasOwn(storage.users, String(storage.activeUserId))) {
            selectedItems = storage.users[String(storage.activeUserId)];
            storage.guest = { ownerUserId: storage.activeUserId, items: selectedItems };
          } else {
            selectedItems = storage.guest.items;
          }
        } else if (storage.activeUserId === currentUserId && Object.hasOwn(storage.users, String(currentUserId))) {
          selectedItems = storage.users[String(currentUserId)];
        } else if (storage.guest.ownerUserId === currentUserId) {
          selectedItems = storage.guest.items;
        } else if (Object.hasOwn(storage.users, String(currentUserId))) {
          selectedItems = storage.users[String(currentUserId)];
        } else if (storage.guest.ownerUserId === null) {
          selectedItems = storage.guest.items;
          storage.guest.ownerUserId = currentUserId;
        } else {
          selectedItems = [];
        }
      } else if (currentUserId === null) {
        storage.users[String(previousUserId)] = currentItems;
        storage.guest = { ownerUserId: previousUserId, items: currentItems };
        selectedItems = currentItems;
      } else {
        if (previousUserId !== null) storage.users[String(previousUserId)] = currentItems;
        if (storage.guest.ownerUserId === currentUserId) {
          selectedItems = storage.guest.items;
        } else if (Object.hasOwn(storage.users, String(currentUserId))) {
          selectedItems = storage.users[String(currentUserId)];
        } else if (storage.guest.ownerUserId === null) {
          selectedItems = storage.guest.items;
          storage.guest.ownerUserId = currentUserId;
        } else {
          selectedItems = [];
        }
      }

      if (currentUserId !== null) storage.users[String(currentUserId)] = selectedItems;
      storage.activeUserId = currentUserId;
      activeUserRef.current = currentUserId;
      itemsRef.current = selectedItems;
      writeCartStorage(storage);
      setItems(selectedItems);
      setHydrated(true);
    });
    return () => { active = false; };
  }, [authLoading, user?.id]);

  useEffect(() => {
    if (!hydrated || activeUserRef.current === undefined) return;
    const storage = readCartStorage();
    const activeUserId = activeUserRef.current;
    if (activeUserId === null) storage.guest.items = items;
    else storage.users[String(activeUserId)] = items;
    writeCartStorage(storage);
  }, [hydrated, items]);

  const value = useMemo<CartContextValue>(() => ({
    items,
    isHydrated: hydrated,
    totalItems: items.reduce((total, item) => total + item.quantity, 0),
    addItem: (product, quantity, requestedVariantId) => setItems((current) => {
      const productId = normalizeProductId(product.id);
      const variantId = requestedVariantId ? normalizeProductId(requestedVariantId) ?? undefined : undefined;
      if (!productId || !Number.isSafeInteger(quantity) || quantity <= 0) return current;
      const key = itemKey(productId, variantId);
      const existing = current.find((item) => itemKey(item.productId, item.variantId) === key);
      if (existing) return current.map((item) => itemKey(item.productId, item.variantId) === key ? { ...item, quantity: item.quantity + quantity } : item);
      return [...current, { productId, variantId, quantity }];
    }),
    increaseItem: (productId, variantId) => setItems((current) => current.map((item) => itemKey(item.productId, item.variantId) === itemKey(productId, variantId) ? { ...item, quantity: item.quantity + 1 } : item)),
    decreaseItem: (productId, variantId) => setItems((current) => current.flatMap((item) => itemKey(item.productId, item.variantId) === itemKey(productId, variantId) ? (item.quantity > 1 ? [{ ...item, quantity: item.quantity - 1 }] : []) : [item])),
    removeItem: (productId, variantId) => setItems((current) => current.filter((item) => itemKey(item.productId, item.variantId) !== itemKey(productId, variantId))),
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
