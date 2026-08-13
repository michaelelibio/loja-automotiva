const TOKEN_KEY = 'garage_access_token';

export function getToken(): string | null {
  try {
    return typeof window !== 'undefined' ? window.localStorage.getItem(TOKEN_KEY) : null;
  } catch {
    return null;
  }
}

export function setToken(token: string) {
  try {
    if (typeof window !== 'undefined') window.localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // ignore
  }
}

export function removeToken() {
  try {
    if (typeof window !== 'undefined') window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
}

export default { getToken, setToken, removeToken };
