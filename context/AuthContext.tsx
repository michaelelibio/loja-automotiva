'use client';

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { User, LoginRequest, RegisterRequest } from '@/lib/types/auth';
import * as authAPI from '@/lib/api/auth';

type AuthContextValue = {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginRequest) => Promise<{ success: boolean; message?: string }>;
  register: (payload: RegisterRequest) => Promise<{ success: boolean; message?: string }>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    async function restore() {
      try {
        const current = await authAPI.getCurrentUser();
        if (!mounted) return;
        setUser(current);
      } catch (err) {
        // token invalid or not present
        setUser(null);
      } finally {
        if (mounted) setIsLoading(false);
      }
    }

    restore();
    return () => { mounted = false; };
  }, []);

  const login = async (payload: LoginRequest) => {
    setIsLoading(true);
    try {
      const data = await authAPI.login(payload);
      setUser(data.user ?? null);
      return { success: true };
    } catch (err: any) {
      return { success: false, message: err?.message ?? 'Erro ao autenticar' };
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterRequest) => {
    setIsLoading(true);
    try {
      const data = await authAPI.register(payload);
      if (data && data.user) setUser(data.user);
      return { success: true };
    } catch (err: any) {
      return { success: false, message: err?.message ?? 'Erro ao cadastrar' };
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    authAPI.logout();
    setUser(null);
  };

  const value = useMemo(() => ({ user, isAuthenticated: !!user, isLoading, login, register, logout }), [user, isLoading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
