import tokenStorage from '@/lib/auth/tokenStorage';

type JwtPayload = { roles?: unknown };

export function hasRole(role: string): boolean {
  const token = tokenStorage.getToken();
  if (!token) return false;
  try {
    const encoded = token.split('.')[1];
    if (!encoded) return false;
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(encoded.length / 4) * 4, '=');
    const payload = JSON.parse(decodeURIComponent(Array.from(atob(normalized), (character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''))) as JwtPayload;
    return Array.isArray(payload.roles) && payload.roles.includes(role);
  } catch {
    return false;
  }
}
