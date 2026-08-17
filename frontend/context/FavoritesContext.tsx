'use client';

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
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
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const [favorites, setFavorites] = useState<Product[]>([]);
  const [favoriteCount, setFavoriteCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestVersion = useRef(0);

  const refreshFavorites = useCallback(async () => {
    if (isAuthLoading) return;

    const request = ++requestVersion.current;
    const authenticatedUserId = user?.id;

    if (!authenticatedUserId) {
      setFavorites([]);
      setFavoriteCount(0);
      setError(null);
      return;
    }

    setIsLoading(true);
    try {
      const data = await favoritesAPI.getFavorites();
      if (request !== requestVersion.current) return;
      setFavorites(data);
      try {
        const count = await favoritesAPI.getFavoriteCount();
        if (request === requestVersion.current) setFavoriteCount(count);
      } catch (countError) {
        if (request !== requestVersion.current) return;
        console.warn('Não foi possível atualizar o contador de favoritos:', countError);
        setFavoriteCount(data.length);
      }
      setError(null);
    } catch (err: unknown) {
      if (request === requestVersion.current) {
        setError(err instanceof Error ? err.message : 'Falha ao carregar favoritos.');
      }
    } finally {
      if (request === requestVersion.current) setIsLoading(false);
    }
  }, [isAuthLoading, user?.id]);

  useEffect(() => {
    queueMicrotask(() => void refreshFavorites());
    return () => { requestVersion.current += 1; };
  }, [refreshFavorites]);

  const isFavorite = useCallback(
    (productId: string) => isAuthenticated && favorites.some((item) => item.id === productId),
    [favorites, isAuthenticated],
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
      } catch (error: unknown) {
        await refreshFavorites();
        throw error;
      }
    },
    [favorites, isAuthenticated, refreshFavorites],
  );

  const value = useMemo(
    () => ({ favorites: isAuthenticated ? favorites : [], favoriteCount: isAuthenticated ? favoriteCount : 0, isFavorite, isLoading: isAuthenticated && isLoading, error: isAuthenticated ? error : null, refreshFavorites, toggleFavorite }),
    [favorites, favoriteCount, isAuthenticated, isFavorite, isLoading, error, refreshFavorites, toggleFavorite],
  );

  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites() {
  const context = useContext(FavoritesContext);
  if (!context) throw new Error('useFavorites must be used inside FavoritesProvider');
  return context;
}
