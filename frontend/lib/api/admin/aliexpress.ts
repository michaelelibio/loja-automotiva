import tokenStorage from '@/lib/auth/tokenStorage';
import type { AliExpressImportRequest, AliExpressImportResult, AliExpressPreview } from '@/lib/types/admin-aliexpress';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AliExpressPreviewError extends Error {
  constructor(public readonly status: number) {
    super(previewErrorMessage(status));
    this.name = 'AliExpressPreviewError';
  }
}

export class AliExpressImportError extends Error {
  constructor(public readonly status: number, public readonly code = '') {
    super(code === 'DUPLICATE' ? 'Este produto ou SKU já foi importado. Nenhum cadastro foi alterado.'
      : code === 'STALE_PREVIEW' ? 'Os dados do fornecedor mudaram. Busque o produto novamente e revise a importação.'
      : code === 'AUTHORIZATION_REQUIRED' ? 'A integração AliExpress precisa ser autorizada pelo administrador.'
      : status === 400 ? 'Revise nome, categoria, variantes e preços de venda. Os preços selecionados devem ser iguais.'
      : status === 409 ? 'Não foi possível confirmar esta importação. Consulte o produto novamente.'
      : status >= 500 ? 'Não foi possível confirmar a importação. Consulte o produto novamente antes de tentar outra vez.'
      : previewErrorMessage(status));
    this.name = 'AliExpressImportError';
  }
}

export async function importAliExpressProduct(payload: AliExpressImportRequest): Promise<AliExpressImportResult> {
  const token = tokenStorage.getToken();
  if (!token) throw new AliExpressImportError(401);
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/admin/integrations/aliexpress/import`, {
      method: 'POST', cache: 'no-store',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  } catch { throw new AliExpressImportError(0); }
  if (!response.ok) {
    let code = '';
    // Read only a fixed public error code, never supplier messages or diagnostics.
    if (response.status === 409) {
      try {
        const data = await response.json();
        if (['DUPLICATE', 'STALE_PREVIEW', 'AUTHORIZATION_REQUIRED'].includes(data?.fields?.code)) code = data.fields.code;
      } catch { /* Use the safe status message. */ }
    }
    throw new AliExpressImportError(response.status, code);
  }
  try {
    const data: AliExpressImportResult = await response.json();
    if (!Number.isSafeInteger(data?.productId) || data.productId <= 0 || data.active !== false) throw new Error('Invalid import result');
    return data;
  } catch { throw new AliExpressImportError(502); }
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
