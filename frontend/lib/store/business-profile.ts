/** Public business information. Populate only with details approved for publication by the owner. */
export type BusinessProfile = {
  legalName: string | null;
  taxIdType: 'CPF' | 'CNPJ' | null;
  taxId: string | null;
  address: string | null;
  supportEmail: string | null;
  privacyEmail: string | null;
  phone: string | null;
  supportHours: string | null;
  deliveryTerms: string | null;
  returnInstructions: string | null;
  refundTerms: string | null;
  privacyRetention: string | null;
  privacyTransfers: string | null;
  policyUpdatedAt: string | null;
  pendingFields: string[];
  ready: boolean;
};

type Environment = Record<string, string | undefined>;
const prefix = 'INGARAGE_PUBLIC_';
const placeholder = /(?:\[[^\]]*\]|a definir|a confirmar|preencha|seu_|sua_|example\.(?:com|org)|exemplo\.|\.test\b|\.invalid\b|\.example\b|localhost)/i;

function text(value: string | undefined, maximum = 2500): string | null {
  const clean = value?.trim();
  return clean && clean.length <= maximum && !/[\u0000-\u001f\u007f]/.test(clean) && !placeholder.test(clean) ? clean : null;
}

function email(value: string | undefined): string | null {
  const clean = text(value, 254);
  return clean && /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$/.test(clean) ? clean : null;
}

function date(value: string | undefined): string | null {
  const clean = value?.trim();
  if (!clean || !/^\d{4}-\d{2}-\d{2}$/.test(clean)) return null;
  const parsed = new Date(`${clean}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === clean ? clean : null;
}

function taxIdValid(value: string, type: 'CPF' | 'CNPJ'): boolean {
  if (!(type === 'CPF' ? /^\d{11}$/ : /^[A-Z0-9]{12}\d{2}$/).test(value) || /^(.)\1+$/.test(value)) return false;
  const baseLength = type === 'CPF' ? 9 : 12;
  for (let length = baseLength; length < baseLength + 2; length++) {
    let sum = 0;
    for (let index = 0; index < length; index++) {
      const weight = type === 'CPF' ? length + 1 - index : (length - index - 1) % 8 + 2;
      sum += (value.charCodeAt(index) - 48) * weight;
    }
    const digit = type === 'CPF' ? (sum * 10) % 11 % 10 : sum % 11 < 2 ? 0 : 11 - sum % 11;
    if (digit !== Number(value[length])) return false;
  }
  return true;
}

/** Missing or unconfirmed values are never converted into published commercial facts. */
export function readBusinessProfile(environment: Environment = process.env): BusinessProfile {
  const get = (key: string) => environment[`${prefix}${key}`];
  const businessConfirmed = get('BUSINESS_CONFIRMED') === 'true';
  const policiesConfirmed = get('POLICIES_CONFIRMED') === 'true';
  const business = (key: string, maximum?: number) => businessConfirmed ? text(get(key), maximum) : null;
  const policy = (key: string) => policiesConfirmed ? text(get(key)) : null;
  const type = get('TAX_ID_TYPE');
  const taxIdType = businessConfirmed && (type === 'CPF' || type === 'CNPJ') ? type : null;
  const rawTaxId = business('TAX_ID', 30)?.replace(/[.\/-]/g, '').toUpperCase() ?? null;
  // CNPJ may include letters in its first 12 positions. Owner confirmation is required in all cases.
  const taxId = taxIdType && rawTaxId && taxIdValid(rawTaxId, taxIdType) ? rawTaxId : null;
  const rawPhone = business('SUPPORT_PHONE', 20);
  const values: Omit<BusinessProfile, 'pendingFields' | 'ready'> = {
    legalName: business('LEGAL_NAME', 200), taxIdType, taxId,
    address: business('BUSINESS_ADDRESS', 500),
    supportEmail: businessConfirmed ? email(get('SUPPORT_EMAIL')) : null,
    privacyEmail: businessConfirmed ? email(get('PRIVACY_EMAIL')) : null,
    phone: rawPhone && /^\+55\d{10,11}$/.test(rawPhone) ? rawPhone : null,
    supportHours: business('SUPPORT_HOURS', 300),
    deliveryTerms: policy('DELIVERY_TERMS'), returnInstructions: policy('RETURN_INSTRUCTIONS'),
    refundTerms: policy('REFUND_TERMS'), privacyRetention: policy('PRIVACY_RETENTION'),
    privacyTransfers: policy('PRIVACY_TRANSFERS'),
    policyUpdatedAt: policiesConfirmed ? date(get('POLICY_UPDATED_AT')) : null,
  };
  const required = {
    LEGAL_NAME: values.legalName, TAX_ID_TYPE: values.taxIdType, TAX_ID: values.taxId,
    BUSINESS_ADDRESS: values.address, SUPPORT_EMAIL: values.supportEmail,
    PRIVACY_EMAIL: values.privacyEmail, SUPPORT_HOURS: values.supportHours,
    DELIVERY_TERMS: values.deliveryTerms, RETURN_INSTRUCTIONS: values.returnInstructions,
    REFUND_TERMS: values.refundTerms, PRIVACY_RETENTION: values.privacyRetention,
    PRIVACY_TRANSFERS: values.privacyTransfers, POLICY_UPDATED_AT: values.policyUpdatedAt,
  };
  const pendingFields = Object.entries(required).filter(([, value]) => !value).map(([key]) => `${prefix}${key}`);
  if (!businessConfirmed) pendingFields.push(`${prefix}BUSINESS_CONFIRMED`);
  if (!policiesConfirmed) pendingFields.push(`${prefix}POLICIES_CONFIRMED`);
  if (get('SUPPORT_PHONE')?.trim() && !values.phone) pendingFields.push(`${prefix}SUPPORT_PHONE`);
  return { ...values, pendingFields, ready: pendingFields.length === 0 };
}
