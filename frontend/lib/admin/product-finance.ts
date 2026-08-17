export function moneyToCents(value: string): number | null {
  const raw = value.trim().replace(/\s/g, '').replace(/^R\$/i, '');
  const normalized = raw.includes(',')
    ? raw.replace(/\./g, '').replace(',', '.')
    : /^\d+\.\d{1,2}$/.test(raw) ? raw : raw.replace(/\./g, '');
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return null;
  const [whole, decimals = ''] = normalized.split('.');
  const cents = Number(whole) * 100 + Number(decimals.padEnd(2, '0'));
  return Number.isSafeInteger(cents) ? cents : null;
}

export function apiMoneyToCents(value: number): number {
  return Math.round(value * 100);
}

export function centsToInput(value: number | null): string {
  return value === null ? '' : (value / 100).toFixed(2).replace('.', ',');
}

export function productMargin(price: number, costPrice: number) {
  const priceCents = apiMoneyToCents(price);
  const costCents = apiMoneyToCents(costPrice);
  const marginCents = priceCents - costCents;
  const percentage = priceCents > 0 ? marginCents * 100 / priceCents : 0;
  return { marginCents, percentage };
}
