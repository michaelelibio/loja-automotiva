'use client';

import { useEffect, useState } from 'react';
import { fetchProducts } from '@/lib/products';
import type { Product } from '@/lib/products';

export function useProducts() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadProducts() {
      try {
        setLoading(true);
        const data = await fetchProducts();
        if (active) setProducts(data);
      } catch {
        if (active) setError('Não foi possível carregar os produtos.');
      } finally {
        if (active) setLoading(false);
      }
    }

    loadProducts();
    return () => {
      active = false;
    };
  }, []);

  return { products, loading, error };
}
