'use client';

import { useEffect, useState } from 'react';
import type { Product } from '@/lib/products';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
const productsEndpoint = `${API_BASE_URL}/api/products`;

export function useProducts() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadProducts() {
      try {
        setLoading(true);
        const response = await fetch(productsEndpoint);
        if (!response.ok) {
          throw new Error('Falha ao carregar os produtos.');
        }
        const data = await response.json();
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
