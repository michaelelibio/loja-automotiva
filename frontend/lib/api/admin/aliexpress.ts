import tokenStorage from '@/lib/auth/tokenStorage';
import type { AliExpressImportRequest, AliExpressImportResult, AliExpressPreview } from '@/lib/types/admin-aliexpress';
import { parseFreightDiagnostic, type FreightDiagnosticResult } from '@/lib/admin/aliexpress-freight';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export class AliExpressFreightError extends Error {
  constructor(public readonly status: number) {
    super(status === 401 ? 'Sua sessão expirou. Entre novamente para testar a cotação.'
      : status === 403 ? 'Acesso negado. Esta consulta exige uma conta de administrador.'
      : status === 404 ? 'Produto ou diagnóstico não encontrado neste servidor.'
      : status === 409 ? 'A autorização AliExpress não está disponível. Confira a integração.'
      : status === 400 ? 'Confira o ID do produto e os parâmetros do teste.'
      : status === 429 ? 'Muitas consultas. Aguarde antes de testar novamente.'
      : status >= 500 ? 'O serviço de diagnóstico está indisponível ou retornou uma resposta inválida. Tente novamente mais tarde.'
      : status === 0 ? 'Não foi possível conectar ao serviço de diagnóstico. Confira sua conexão e tente novamente.'
      : 'Não foi possível executar o diagnóstico de frete. Tente novamente mais tarde.');
    this.name = 'AliExpressFreightError';
  }
}

export type FreightTestInput = { productId: string; skuId: string; quantity: number };
export type FreightProduct = { productId: string; variants: { skuId: string; availableStock: number }[] };

export async function prepareAliExpressFreight(productId: string): Promise<FreightProduct> {
  const token = tokenStorage.getToken();
  if (!token) throw new AliExpressFreightError(401);
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/admin/integrations/aliexpress/freight-diagnostic/product`, {
      method: 'POST', cache: 'no-store', signal: AbortSignal.timeout(35000),
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId }),
    });
  } catch { throw new AliExpressFreightError(0); }
  if (!response.ok) throw new AliExpressFreightError(response.status);
  try {
    const data = await response.json();
    if (data.productId !== productId || !Array.isArray(data.variants) || data.variants.length > 1000) throw new Error();
    const variants = data.variants.map((v: FreightProduct['variants'][number]) => {
      if (!v || typeof v.skuId !== 'string' || !/^[0-9]{1,30}$/.test(v.skuId) || !Number.isSafeInteger(v.availableStock) || v.availableStock < 0) throw new Error();
      return { skuId: v.skuId, availableStock: v.availableStock };
    });
    return { productId, variants };
  } catch { throw new AliExpressFreightError(502); }
}

export async function testAliExpressFreight(input?: FreightTestInput): Promise<FreightDiagnosticResult> {
  const token = tokenStorage.getToken();
  if (!token) throw new AliExpressFreightError(401);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 65000);
  try {
    let response: Response;
    try {
      response = await fetch(`${API_BASE}/api/admin/integrations/aliexpress/freight-diagnostic`, {
        method: 'POST', cache: 'no-store', signal: controller.signal,
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', 'Content-Type': 'application/json' },
        body: input ? JSON.stringify(input) : undefined,
      });
    } catch { throw new AliExpressFreightError(0); }
    if (!response.ok) throw new AliExpressFreightError(response.status);
    try { return parseFreightDiagnostic(await response.json(), token); }
    catch { throw new AliExpressFreightError(502); }
  } finally { clearTimeout(timeout); }
}

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
