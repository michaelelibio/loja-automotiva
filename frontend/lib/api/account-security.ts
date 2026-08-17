import tokenStorage from '@/lib/auth/tokenStorage';
import type { AccountActionResponse, ChangePasswordRequest, ForgotPasswordRequest, ResetPasswordRequest, VerifyEmailRequest } from '@/lib/types/account-security';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/auth`;

export class AccountSecurityApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'AccountSecurityApiError'; }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${endpoint}/${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? Object.values(object.fields as Record<string, unknown>).join(' ') : null;
    throw new AccountSecurityApiError(String(fields || object?.message || object?.error || 'Não foi possível concluir a solicitação.'), response.status);
  }
  return data as T;
}

export function verifyEmail(data: VerifyEmailRequest) { return post<AccountActionResponse>('verify-email', data); }
export function forgotPassword(data: ForgotPasswordRequest) { return post<AccountActionResponse>('forgot-password', data); }
export function resetPassword(data: ResetPasswordRequest) { return post<AccountActionResponse>('reset-password', data); }

export async function changePassword(data: ChangePasswordRequest): Promise<AccountActionResponse> {
  const token = tokenStorage.getToken();
  if (!token) throw new AccountSecurityApiError('Sua sessão expirou. Entre novamente.', 401);
  const response = await fetch(`${API_BASE}/api/account/password`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  const text = await response.text();
  let responseData: unknown = null;
  try { responseData = text ? JSON.parse(text) : null; } catch { responseData = text; }
  if (!response.ok) {
    const object = responseData && typeof responseData === 'object' ? responseData as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object'
      ? Object.values(object.fields as Record<string, unknown>).join(' ') : null;
    throw new AccountSecurityApiError(String(fields || object?.message || object?.error || 'Não foi possível alterar sua senha.'), response.status);
  }
  return responseData as AccountActionResponse;
}
