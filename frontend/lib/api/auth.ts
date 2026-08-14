import type { LoginRequest, RegisterRequest, UpdateUserRequest, AuthResponse, User } from '@/lib/types/auth';
import tokenStorage from '@/lib/auth/tokenStorage';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AuthApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'AuthApiError';
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  const text = await response.text();

  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const errorData = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const message = errorData?.message ?? errorData?.error ?? errorData?.errors ?? text ?? response.statusText;
    throw new AuthApiError(String(message || `Erro na requisição (${response.status})`), response.status);
  }

  return data as T;
}

export async function login(payload: LoginRequest): Promise<AuthResponse> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await handleResponse<AuthResponse>(res);
  if (data?.accessToken) tokenStorage.setToken(data.accessToken);
  return data;
}

export async function register(payload: RegisterRequest): Promise<AuthResponse | null> {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await handleResponse<AuthResponse>(res);
  if (data?.accessToken) tokenStorage.setToken(data.accessToken);
  return data;
}

export async function getCurrentUser(): Promise<User> {
  const token = tokenStorage.getToken();
  if (!token) throw new Error('Sem token');

  const res = await fetch(`${API_BASE}/api/users/me`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });

  return handleResponse<User>(res);
}

export function hasStoredToken(): boolean {
  return Boolean(tokenStorage.getToken());
}

export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof AuthApiError && error.status === 401;
}

export async function updateCurrentUser(payload: UpdateUserRequest): Promise<User> {
  const token = tokenStorage.getToken();
  if (!token) throw new Error('Sem token');

  const res = await fetch(`${API_BASE}/api/users/me`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  return handleResponse<User>(res);
}

export function logout(): void {
  tokenStorage.removeToken();
}

export default { login, register, getCurrentUser, hasStoredToken, isUnauthorizedError, updateCurrentUser, logout };
