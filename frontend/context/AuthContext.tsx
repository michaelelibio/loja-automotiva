'use client';

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { User, LoginRequest, RegisterRequest, UpdateUserRequest } from '@/lib/types/auth';
import * as authAPI from '@/lib/api/auth';

type AuthContextValue = {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  sessionError: string | null;
  login: (payload: LoginRequest) => Promise<{ success: boolean; message?: string }>;
  loginWithGoogle: (credential: string) => Promise<{ success: boolean; message?: string }>;
  register: (payload: RegisterRequest) => Promise<{ success: boolean; message?: string }>;
  updateProfile: (payload: UpdateUserRequest) => Promise<{ success: boolean; message?: string }>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [sessionError, setSessionError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    async function restore() {
      if (!authAPI.hasStoredToken()) {
        if (mounted) setIsLoading(false);
        return;
      }

      try {
        const current = await authAPI.getCurrentUser();
        if (!mounted) return;
        setUser(current);
        setSessionError(null);
      } catch (err) {
        if (!mounted) return;
        if (authAPI.isUnauthorizedError(err)) {
          authAPI.logout();
          setUser(null);
          setSessionError(null);
        } else {
          setSessionError('Não foi possível verificar sua sessão. Tente novamente em instantes.');
        }
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
      setSessionError(null);
      return { success: true };
    } catch (err: unknown) {
      return { success: false, message: err instanceof Error ? err.message : 'Erro ao autenticar' };
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterRequest) => {
    setIsLoading(true);
    try {
      const data = await authAPI.register(payload);
      if (data && data.user) setUser(data.user);
      setSessionError(null);
      return { success: true };
    } catch (err: unknown) {
      return { success: false, message: err instanceof Error ? err.message : 'Erro ao cadastrar' };
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    authAPI.logout();
    setUser(null);
    setSessionError(null);
  };

  const loginWithGoogle = async (credential: string) => {
    setIsLoading(true);
    try {
      const data = await authAPI.googleLogin({ credential });
      setUser(data.user ?? null);
      setSessionError(null);
      return { success: true };
    } catch (err: unknown) {
      const message = err instanceof authAPI.AuthApiError && err.status === 409
        ? 'Já existe uma conta com este e-mail. Entre com e-mail e senha.'
        : 'Não foi possível entrar com o Google. Tente novamente.';
      return { success: false, message };
    } finally {
      setIsLoading(false);
    }
  };

  const updateProfile = async (payload: UpdateUserRequest) => {
    try {
      const updatedUser = await authAPI.updateCurrentUser(payload);
      setUser(updatedUser);
      setSessionError(null);
      return { success: true };
    } catch (err: unknown) {
      if (authAPI.isUnauthorizedError(err)) {
        authAPI.logout();
        setUser(null);
        setSessionError(null);
      }
      return { success: false, message: err instanceof Error ? err.message : 'Não foi possível salvar as alterações.' };
    }
  };

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, isLoading, sessionError, login, loginWithGoogle, register, updateProfile, logout }),
    [user, isLoading, sessionError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
