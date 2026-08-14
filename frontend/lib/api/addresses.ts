import tokenStorage from '@/lib/auth/tokenStorage';
import type { Address, AddressRequest } from '@/lib/types/address';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const endpoint = `${API_BASE}/api/addresses`;

export class AddressApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); this.name = 'AddressApiError'; }
}

function headers(json = false): HeadersInit {
  const token = tokenStorage.getToken();
  if (!token) throw new AddressApiError('Sessão não encontrada. Entre novamente.', 401);
  return { Authorization: `Bearer ${token}`, ...(json ? { 'Content-Type': 'application/json' } : {}) };
}

async function parse<T>(response: Response): Promise<T> {
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    const object = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = object?.fields && typeof object.fields === 'object' ? Object.values(object.fields as Record<string, unknown>).join(' ') : null;
    throw new AddressApiError(String(fields || object?.message || object?.error || text || response.statusText), response.status);
  }
  return data as T;
}

export async function getAddresses(): Promise<Address[]> {
  return parse<Address[]>(await fetch(endpoint, { headers: headers(), cache: 'no-store' }));
}
export async function createAddress(data: AddressRequest): Promise<Address> {
  return parse<Address>(await fetch(endpoint, { method: 'POST', headers: headers(true), body: JSON.stringify(data) }));
}
export async function updateAddress(id: number, data: AddressRequest): Promise<Address> {
  return parse<Address>(await fetch(`${endpoint}/${encodeURIComponent(id)}`, { method: 'PUT', headers: headers(true), body: JSON.stringify(data) }));
}
export async function deleteAddress(id: number): Promise<void> {
  await parse<void>(await fetch(`${endpoint}/${encodeURIComponent(id)}`, { method: 'DELETE', headers: headers() }));
}
export async function setPrimaryAddress(id: number): Promise<Address> {
  return parse<Address>(await fetch(`${endpoint}/${encodeURIComponent(id)}/primary`, { method: 'PATCH', headers: headers() }));
}
