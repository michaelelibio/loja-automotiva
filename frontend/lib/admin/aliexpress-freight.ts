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
export type FreightDiagnosticMethod = { serviceName: string; amount: number | string; currency: string; estimatedDeliveryTime: string | null; company?: string | null; originCountry?: string | null };
export type MoneyField = { optionIndex: number; path: string; present: boolean; jsonType: string; currency: string | null; documentedScale: string; safeValue: string | null };
export type FreightDiagnosticResult = { status: FreightDiagnosticStatus; safeCode: string; methods: FreightDiagnosticMethod[]; monetaryStructure?: MoneyField[] };

const protocolMessages = {
  TOP_21_MISSING_METHOD: 'O AliExpress retornou o código 21: método ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_22_INVALID_METHOD: 'O AliExpress retornou o código 22: método não reconhecido neste endpoint. Confirme o transporte e a disponibilidade desta API para a aplicação.',
  TOP_24_MISSING_SIGNATURE: 'O AliExpress retornou o código 24: assinatura ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_25_INVALID_SIGNATURE: 'O AliExpress retornou o código 25: assinatura rejeitada. Confira o contrato de assinatura, o transporte e os parâmetros enviados; nenhuma credencial foi alterada.',
  TOP_28_MISSING_APP_KEY: 'O AliExpress retornou o código 28: identificação da aplicação ausente na requisição. Revise o formato e o transporte da consulta.',
  TOP_29_INVALID_APP_KEY: 'O AliExpress retornou o código 29: identificação da aplicação rejeitada neste endpoint. Isso não comprova que a credencial seja inválida em outro ambiente.',
} as const;

export function freightDiagnosticMessage(result: FreightDiagnosticResult): string {
  if (result.safeCode === 'AMBIGUOUS_MONETARY_UNIT') return 'A API retornou uma unidade monetária ambígua. O contrato descreve centavos, mas há inconsistência no exemplo oficial. Nenhum valor foi presumido ou convertido.';
  if (result.safeCode === 'PROVIDER_DELIVERY_UNAVAILABLE') return 'A API informou que não há entrega para o destino suportado nesta consulta.';
  if (result.safeCode === 'PROVIDER_PRODUCT_ID_REJECTED') return 'A API retornou DELIVERY_INFO_EMPTY, documentado como produto inválido. Confira produto e SKU.';
  if (result.safeCode === 'SHIPPING_ORIGIN_UNCONFIRMED') return 'Origem de envio ausente ou ambígua nos atributos Ships From do SKU. A cotação foi bloqueada; país da loja e destino não comprovam a origem.';
  if (result.safeCode === 'PRODUCT_LOOKUP_FAILED') return 'A consulta prévia do produto falhou. O status se refere a essa etapa; a API de frete não foi chamada.';
  if (result.safeCode === 'INVALID_TEST_INPUT') return 'Informe um produto, SKU e quantidade válidos.';
  if (result.safeCode === 'SKU_NOT_IN_PRODUCT') return 'O SKU não consta no produto consultado. Consulte os SKUs novamente.';
  if (result.safeCode === 'INSUFFICIENT_SUPPLIER_STOCK') return 'A quantidade excede o estoque retornado para o SKU.';
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
  'UNCLASSIFIED_PROVIDER_REJECTION', 'INVALID_TEST_INPUT', 'PRODUCT_LOOKUP_FAILED', 'SKU_NOT_IN_PRODUCT',
  'SHIPPING_ORIGIN_UNCONFIRMED', 'INSUFFICIENT_SUPPLIER_STOCK', 'AMBIGUOUS_MONETARY_UNIT',
  'PROVIDER_DELIVERY_UNAVAILABLE', 'PROVIDER_PRODUCT_ID_REJECTED', 'CONTRADICTORY_SUCCESS_CODE', 'INVALID_DELIVERY_METHOD',
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
        || entry.serviceName.includes(token) || !((typeof entry.amount === 'number' && Number.isFinite(entry.amount) && entry.amount >= 0)
          || (typeof entry.amount === 'string' && /^[0-9]{1,14}\.[0-9]{2}$/.test(entry.amount)))
        || typeof entry.currency !== 'string' || entry.currency.length !== 3 || !/^[A-Z]{3}$/.test(entry.currency)) throw new Error('Invalid method');
    const details: { company?: string | null; originCountry?: string | null } = {};
    if ('company' in entry) details.company = typeof entry.company === 'string' && /^[A-Za-z][A-Za-z0-9 ._()/ -]{0,119}$/.test(entry.company) && !/[0-9]{8,}/.test(entry.company) && !entry.company.includes(token) ? entry.company : null;
    if ('originCountry' in entry) details.originCountry = typeof entry.originCountry === 'string' && /^[A-Z]{2}$/.test(entry.originCountry) ? entry.originCountry : null;
    return { ...details, serviceName: entry.serviceName, amount: entry.amount, currency: entry.currency,
      estimatedDeliveryTime: typeof entry.estimatedDeliveryTime === 'string' && estimatePattern.test(entry.estimatedDeliveryTime)
        ? entry.estimatedDeliveryTime : null };
  });
  if (new Set(methods.map((method) => method.serviceName)).size !== methods.length
      || (value.status === 'SUCCESS' && methods.length === 0)
      || (value.status !== 'SUCCESS' && methods.length !== 0)) throw new Error('Invalid diagnostic');
  const fields = ['shipping_fee_cent','shipping_fee_currency','shipping_fee_format','free_shipping','shipping_fee','amount','cent','currency_code','free_shipping_threshold','shipping_fee.amount','shipping_fee.cent','shipping_fee.currency_code','amount.amount','amount.cent','amount.currency_code'];
  const monetaryStructure: MoneyField[] = [];
  if (Array.isArray(value.monetaryStructure) && value.monetaryStructure.length <= 7500) {
    for (const f of value.monetaryStructure) {
      if (!object(f) || !Number.isInteger(f.optionIndex) || (f.optionIndex as number) < 0 || (f.optionIndex as number) >= 500
          || typeof f.path !== 'string' || !fields.includes(f.path) || typeof f.present !== 'boolean'
          || typeof f.jsonType !== 'string' || !['MISSING','NULL','STRING','NUMBER','BOOLEAN','OBJECT','ARRAY'].includes(f.jsonType)
          || typeof f.documentedScale !== 'string' || !['CENT_1_100_DESCRIPTION_EXAMPLE_CONFLICT','DISPLAY_TEXT_ONLY','NOT_DOCUMENTED_AS_AMOUNT'].includes(f.documentedScale)) continue;
      monetaryStructure.push({ optionIndex: f.optionIndex as number, path: f.path, present: f.present, jsonType: f.jsonType,
        documentedScale: f.documentedScale,
        currency: typeof f.currency === 'string' && ['BRL','USD','EUR','GBP','JPY','CNY','CAD','AUD'].includes(f.currency) && !f.currency.includes(token) ? f.currency : null,
        safeValue: typeof f.safeValue === 'string' && /^(?:[0-9]{1,6}(?:\.[0-9]{1,4})?|true|false|BRL|USD|EUR|GBP|JPY|CNY|CAD|AUD)$/.test(f.safeValue) && !f.safeValue.includes(token) ? f.safeValue : null });
    }
  }
  return { status: value.status as FreightDiagnosticStatus,
    safeCode: typeof value.safeCode === 'string' && safeCodes.has(value.safeCode) ? value.safeCode : 'UNRECOGNIZED_RESULT', methods,
    ...(monetaryStructure.length ? { monetaryStructure } : {}) };
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
  if (typeof method.amount === 'string') {
    const [whole, fraction] = method.amount.split('.');
    return method.currency + ' ' + new Intl.NumberFormat('pt-BR').format(BigInt(whole)) + ',' + fraction;
  }
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: method.currency, maximumFractionDigits: 8 }).format(method.amount);
}
