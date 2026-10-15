import tokenStorage from '@/lib/auth/tokenStorage';
import type { AliExpressPreview } from '@/lib/types/admin-aliexpress';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AliExpressPreviewError extends Error {
  constructor(public readonly status: number) {
    super(previewErrorMessage(status));
    this.name = 'AliExpressPreviewError';
  }
}

function previewErrorMessage(status: number): string {
  switch (status) {
    case 400: return 'Informe um ID ou uma URL válida de produto AliExpress.';
    case 401: return 'Sua sessão expirou. Entre novamente para continuar.';
    case 403: return 'Sua conta não possui permissão administrativa para esta consulta.';
    case 404: return 'Produto não encontrado no AliExpress. Confira o ID ou a URL.';
    case 409: return 'A integração AliExpress precisa ser autorizada pelo administrador.';
    case 422: return 'Este produto não está disponível para consulta ou envio ao Brasil.';
    case 429: return 'Limite de consultas atingido. Aguarde um momento e tente novamente.';
    case 504: return 'O AliExpress demorou para responder. Tente novamente.';
    case 0: return 'Não foi possível conectar. Verifique sua conexão e tente novamente.';
    default: return 'Não foi possível consultar o AliExpress agora. Tente novamente mais tarde.';
  }
}

export async function previewAliExpressProduct(input: string): Promise<AliExpressPreview> {
  const cleanInput = input.trim();
  if (!cleanInput || cleanInput.length > 2048) throw new AliExpressPreviewError(400);
  const token = tokenStorage.getToken();
  if (!token) throw new AliExpressPreviewError(401);
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/admin/integrations/aliexpress/preview`, {
      method: 'POST',
      cache: 'no-store',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ input: cleanInput, shipToCountry: 'BR', targetCurrency: 'BRL', targetLanguage: 'pt_BR' }),
    });
  } catch { throw new AliExpressPreviewError(0); }
  // Never propagate backend error bodies: they may contain integration diagnostics.
  if (!response.ok) throw new AliExpressPreviewError(response.status);
  try {
    const data: AliExpressPreview = await response.json();
    if (!data || typeof data.productId !== 'string' || !Array.isArray(data.variants)
      || !Array.isArray(data.images) || !Array.isArray(data.warnings) || !data.attributes) {
      throw new Error('Invalid preview');
    }
    return data;
  } catch { throw new AliExpressPreviewError(502); }
}
