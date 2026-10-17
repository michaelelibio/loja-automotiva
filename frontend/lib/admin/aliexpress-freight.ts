export const freightStatusMessages = {
  SUCCESS: 'A consulta foi concluída e retornou modalidades de entrega.',
  PERMISSION_DENIED: 'O fornecedor negou o acesso à consulta. Confira as permissões da aplicação e as restrições de acesso no AliExpress.',
  TOKEN_REJECTED: 'O AliExpress rejeitou a autorização nesta consulta. Confira a integração; nenhuma credencial foi renovada automaticamente.',
  PRODUCT_UNAVAILABLE: 'Nenhuma modalidade de entrega está disponível para os parâmetros deste teste.',
  PARAMETER_ERROR: 'O AliExpress recusou os parâmetros do teste. O contrato da consulta precisa ser revisado.',
  COMMUNICATION_ERROR: 'Não foi possível obter uma resposta do AliExpress. Tente novamente mais tarde.',
  INVALID_RESPONSE: 'A resposta recebida não permitiu confirmar as modalidades e os valores do frete.',
  PROVIDER_REJECTED: 'O AliExpress recusou a consulta sem informar um motivo que possamos identificar com segurança.',
  LOCAL_AUTHORIZATION_UNAVAILABLE: 'A autorização existente não está disponível ou está expirada. Confira a integração AliExpress.',
  NOT_CONFIGURED: 'A integração AliExpress não está configurada para esta consulta.',
  RATE_LIMITED: 'O limite de consultas foi atingido. Aguarde antes de testar novamente.',
  PROTOCOL_ERROR: 'O AliExpress rejeitou o protocolo da consulta. Revise o contrato e a configuração da integração.',
} as const;

export type FreightDiagnosticStatus = keyof typeof freightStatusMessages;
export type FreightDiagnosticMethod = { serviceName: string; amount: number; currency: string; estimatedDeliveryTime: string | null };
export type FreightDiagnosticResult = { status: FreightDiagnosticStatus; safeCode: string; methods: FreightDiagnosticMethod[] };

const protocolMessages = {
  TOP_21_MISSING_METHOD: 'O AliExpress retornou o código 21: método ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_22_INVALID_METHOD: 'O AliExpress retornou o código 22: método não reconhecido neste endpoint. Confirme o transporte e a disponibilidade desta API para a aplicação.',
  TOP_24_MISSING_SIGNATURE: 'O AliExpress retornou o código 24: assinatura ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_25_INVALID_SIGNATURE: 'O AliExpress retornou o código 25: assinatura rejeitada. Confira o contrato de assinatura, o transporte e os parâmetros enviados; nenhuma credencial foi alterada.',
  TOP_28_MISSING_APP_KEY: 'O AliExpress retornou o código 28: identificação da aplicação ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_29_INVALID_APP_KEY: 'O AliExpress retornou o código 29: identificação da aplicação rejeitada neste endpoint. Isso não comprova que a credencial seja inválida em outro ambiente.',
} as const;

export function freightDiagnosticMessage(result: FreightDiagnosticResult): string {
  if (result.status === 'PROTOCOL_ERROR' && Object.hasOwn(protocolMessages, result.safeCode)) {
    return protocolMessages[result.safeCode as keyof typeof protocolMessages];
  }
  return freightStatusMessages[result.status];
}

const safeCodes = new Set([
  ...Object.keys(protocolMessages),
  'APPLICATION_NOT_CONFIGURED', 'LOCAL_TOKEN_UNAVAILABLE', 'HTTP_RATE_LIMIT', 'HTTP_FAILURE',
  'RESPONSE_TOO_LARGE', 'INVALID_JSON_RESPONSE', 'COMMUNICATION_FAILURE', 'INVALID_RESPONSE_SHAPE',
  'INVALID_METHOD_LIST', 'NO_DELIVERY_METHODS', 'INVALID_METHOD_DATA', 'CONFLICTING_MONETARY_FIELDS',
  'DUPLICATE_SERVICE', 'CALL_SUCCEEDED', 'PROVIDER_TOKEN_REJECTED', 'PROVIDER_PERMISSION_DENIED',
  'PROVIDER_PROTOCOL_REJECTED', 'PROVIDER_RATE_LIMIT', 'PROVIDER_PARAMETER_REJECTED',
  'UNCLASSIFIED_PROVIDER_REJECTION',
]);
const estimatePattern = /^(\d{1,4})(?:\s*[-~]\s*(\d{1,4}))?\s*(?:days?|d|dias?)?$/;

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

// Copy only known public fields; never forward backend messages, raw errors or extra properties.
export function parseFreightDiagnostic(value: unknown, token: string): FreightDiagnosticResult {
  if (!object(value) || typeof value.status !== 'string'
      || !Object.hasOwn(freightStatusMessages, value.status) || !Array.isArray(value.methods) || value.methods.length > 500) throw new Error('Invalid diagnostic');
  const methods = value.methods.map((entry): FreightDiagnosticMethod => {
    if (!object(entry) || typeof entry.serviceName !== 'string' || entry.serviceName.trim() !== entry.serviceName || !/^[A-Za-z0-9_.-]{1,120}$/.test(entry.serviceName)
        || entry.serviceName.includes(token) || typeof entry.amount !== 'number' || !Number.isFinite(entry.amount) || entry.amount < 0
        || typeof entry.currency !== 'string' || entry.currency.length !== 3 || !/^[A-Z]{3}$/.test(entry.currency)) throw new Error('Invalid method');
    return { serviceName: entry.serviceName, amount: entry.amount, currency: entry.currency,
      estimatedDeliveryTime: typeof entry.estimatedDeliveryTime === 'string' && estimatePattern.test(entry.estimatedDeliveryTime)
        ? entry.estimatedDeliveryTime : null };
  });
  if (new Set(methods.map((method) => method.serviceName)).size !== methods.length
      || (value.status === 'SUCCESS' && methods.length === 0)
      || (value.status !== 'SUCCESS' && methods.length !== 0)) throw new Error('Invalid diagnostic');
  return { status: value.status as FreightDiagnosticStatus,
    safeCode: typeof value.safeCode === 'string' && safeCodes.has(value.safeCode) ? value.safeCode : 'UNRECOGNIZED_RESULT', methods };
}

export function freightEstimate(value: string | null): string {
  const match = value?.match(estimatePattern);
  if (!match) return 'Prazo não informado';
  const minimum = Number(match[1]), maximum = Number(match[2] ?? match[1]);
  if (minimum < 1 || maximum < minimum) return 'Prazo não informado';
  return minimum === maximum ? `${maximum} ${maximum === 1 ? 'dia estimado' : 'dias estimados'}`
    : `Entre ${minimum} e ${maximum} dias (estimativa)`;
}

export function freightAmount(method: FreightDiagnosticMethod): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: method.currency, maximumFractionDigits: 8 }).format(method.amount);
}
