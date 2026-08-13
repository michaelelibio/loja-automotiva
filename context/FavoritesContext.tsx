'use client';

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import * as favoritesAPI from '@/lib/api/favorites';
import type { Product } from '@/lib/products';

type FavoritesContextValue = {
  favorites: Product[];
  favoriteCount: number;
  isFavorite: (productId: string) => boolean;
  isLoading: boolean;
  error: string | null;
  refreshFavorites: () => Promise<void>;
  toggleFavorite: (product: Product) => Promise<void>;
};

const FavoritesContext = createContext<FavoritesContextValue | null>(null);

export function FavoritesProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const [favorites, setFavorites] = useState<Product[]>([]);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshFavorites = useCallback(async () => {
    if (isAuthLoading) return;

    if (!isAuthenticated) {
      setFavorites([]);
      setFavoriteCount(0);
      setError(null);
      return;
    }

    setIsLoading(true);
    try {
      const data = await favoritesAPI.getFavorites();
      setFavorites(data);
      try {
        setFavoriteCount(await favoritesAPI.getFavoriteCount());
      } catch (countError) {
        console.warn('Não foi possível atualizar o contador de favoritos:', countError);
        setFavoriteCount(data.length);
      }
      setError(null);
    } catch (err: any) {
      setError(err?.message ?? 'Falha ao carregar favoritos.');
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated, isAuthLoading]);

  useEffect(() => {
    refreshFavorites();
  }, [isAuthenticated, refreshFavorites]);

  const isFavorite = useCallback(
    (productId: string) => favorites.some((item) => item.id === productId),
    [favorites],
  );

  const toggleFavorite = useCallback(
    async (product: Product) => {
      if (!isAuthenticated) {
        throw new Error('Faça login para salvar favoritos.');
      }

      const currentlyFavorite = favorites.some((item) => item.id === product.id);
      const nextFavorites = currentlyFavorite
        ? favorites.filter((item) => item.id !== product.id)
        : [...favorites, product];

      setFavorites(nextFavorites);
      setFavoriteCount(nextFavorites.length);

      try {
        if (currentlyFavorite) {
          await favoritesAPI.removeFavorite(product.id);
        } else {
          await favoritesAPI.addFavorite(product.id);
        }
      } catch (error: any) {
        await refreshFavorites();
        throw error;
      }
    },
    [favorites, isAuthenticated, refreshFavorites],
  );

  const value = useMemo(
    () => ({ favorites, favoriteCount, isFavorite, isLoading, error, refreshFavorites, toggleFavorite }),
    [favorites, favoriteCount, isFavorite, isLoading, error, refreshFavorites, toggleFavorite],
  );

  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites() {
  const context = useContext(FavoritesContext);
  if (!context) throw new Error('useFavorites must be used inside FavoritesProvider');
  return context;
}
