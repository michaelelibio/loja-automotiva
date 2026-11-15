export const STATES = 'AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' ');
export const SIZES = ['SMALL', 'MEDIUM', 'LARGE'] as const;
export type FreightRate = Record<string, unknown> & {key: string; state: string; size: string; category: string; supplierStoreId: string; originCountry: string};
export type ReviewRow = {rate: FreightRate; approved: boolean};
export type BulkDocument = Record<string, unknown> & {reviewRows: ReviewRow[]};
export type Scope = {category: string; supplierStoreId: string; originCountry: string};
const selector = (r: FreightRate) => JSON.stringify([r.state, r.size, r.category, r.supplierStoreId, r.originCountry]);

/** Adds missing cells only. No costs, logistics, evidence or approval are inferred. */
export function prepareNational(document: BulkDocument, scope: Scope): BulkDocument {
  if (!scope.category.trim() || !scope.supplierStoreId.trim() || !/^[A-Z]{2}$/.test(scope.originCountry)) throw new Error('Informe categoria, fornecedor e origem conferidos no catálogo/perfis.');
  const rows = [...document.reviewRows];
  const existing = new Set(rows.map(r => selector(r.rate)));
  for (const state of STATES) for (const size of SIZES) {
    const rate: FreightRate = {key: 'UF-' + crypto.randomUUID(), state, size, ...scope,
      logisticsCode: '', logisticsName: '', supplierFreightPerParcelBrl: null, customerFreightPerParcelBrl: null,
      taxesPerUnitBrl: null, maximumVerifiedUnits: 0, maximumVerifiedSupplierBaseBrl: null, unitsPerParcel: 0,
      grouping: 'PER_SKU', packagingVerified: false, estimatedDays: 0, evidence: null};
    if (!existing.has(selector(rate))) {rows.push({rate, approved: false}); existing.add(selector(rate));}
  }
  if (rows.length > 2000) throw new Error('Limite de 2000 linhas por versão.');
  return {...document, rates: [], reviewRows: rows};
}

export function importNational(text: string): BulkDocument {
  if (text.length > 4_000_000) throw new Error('Arquivo excede 4 MB.');
  const parsed: unknown = JSON.parse(text);
  if (!parsed || typeof parsed !== 'object' || !('reviewRows' in parsed) || !Array.isArray(parsed.reviewRows) || !parsed.reviewRows.length || parsed.reviewRows.length > 2000) throw new Error('Arquivo deve conter de 1 a 2000 reviewRows.');
  const seen = new Set<string>(); const keys = new Set<string>();
  const rows: ReviewRow[] = parsed.reviewRows.map((row: ReviewRow) => {
    const r = row?.rate;
    if (!r || !STATES.includes(r.state) || !SIZES.some(s => s === r.size) || !r.category?.trim() || !r.supplierStoreId?.trim() || !/^[A-Z]{2}$/.test(r.originCountry) || !r.key?.trim()) throw new Error('Linha sem UF, porte ou escopo válido.');
    if (seen.has(selector(r)) || keys.has(r.key)) throw new Error('Arquivo contém tarifas duplicadas.');
    seen.add(selector(r)); keys.add(r.key);
    return {rate: {...r}, approved: false};
  });
  return {...parsed, rates: [], reviewRows: rows};
}

export function commercialPrice(document: BulkDocument, indices: number[], value: string): BulkDocument {
  if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error('Preço comercial deve ser BRL não negativo, com até duas casas.');
  const selected = new Set(indices);
  return {...document, rates: [], reviewRows: document.reviewRows.map((row, i) => selected.has(i)
    ? {approved: false, rate: {...row.rate, customerFreightPerParcelBrl: value}} : row)};
}

export function fromVersion(document: Record<string, unknown>): BulkDocument {
  const rows = Array.isArray(document.reviewRows) && document.reviewRows.length ? document.reviewRows
    : (Array.isArray(document.rates) ? document.rates.map(rate => ({rate, approved: false})) : []);
  return {...document, rates: [], reviewRows: rows as ReviewRow[]};
}
