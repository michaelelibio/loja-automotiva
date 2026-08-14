import tokenStorage from '@/lib/auth/tokenStorage';
import type { Vehicle, VehicleRequest } from '@/lib/types/vehicle';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const vehiclesEndpoint = `${API_BASE}/api/vehicles`;

export class VehicleApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'VehicleApiError';
  }
}

function authHeaders(existing?: HeadersInit): HeadersInit {
  const token = tokenStorage.getToken();
  if (!token) throw new VehicleApiError('Sessão não encontrada. Entre novamente.', 401);
  return { ...existing, Authorization: `Bearer ${token}` };
}

async function handleResponse<T>(response: Response): Promise<T> {
  const text = await response.text();
  let data: unknown = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (!response.ok) {
    const errorData = data && typeof data === 'object' ? data as Record<string, unknown> : null;
    const fields = errorData?.fields && typeof errorData.fields === 'object'
      ? Object.values(errorData.fields as Record<string, unknown>).join(' ')
      : null;
    const message = fields || errorData?.message || errorData?.error || text || response.statusText;
    throw new VehicleApiError(String(message || `Erro na requisição (${response.status})`), response.status);
  }

  return data as T;
}

export async function getVehicles(): Promise<Vehicle[]> {
  const response = await fetch(vehiclesEndpoint, { headers: authHeaders(), cache: 'no-store' });
  return handleResponse<Vehicle[]>(response);
}

export async function createVehicle(data: VehicleRequest): Promise<Vehicle> {
  const response = await fetch(vehiclesEndpoint, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(data),
  });
  return handleResponse<Vehicle>(response);
}

export async function updateVehicle(id: number, data: VehicleRequest): Promise<Vehicle> {
  const response = await fetch(`${vehiclesEndpoint}/${encodeURIComponent(id)}`, {
    method: 'PUT', headers: authHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(data),
  });
  return handleResponse<Vehicle>(response);
}

export async function deleteVehicle(id: number): Promise<void> {
  const response = await fetch(`${vehiclesEndpoint}/${encodeURIComponent(id)}`, {
    method: 'DELETE', headers: authHeaders(),
  });
  await handleResponse<void>(response);
}

export async function setPrimaryVehicle(id: number): Promise<Vehicle> {
  const response = await fetch(`${vehiclesEndpoint}/${encodeURIComponent(id)}/primary`, {
    method: 'PATCH', headers: authHeaders(),
  });
  return handleResponse<Vehicle>(response);
}
