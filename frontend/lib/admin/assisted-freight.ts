export type FreightObservedOption = {
  optionIndex: number; code: string | null; name: string | null; originCountry: string | null;
  estimatedDays: number | null; receivedAmount: string | null; formattedAmount: string | null;
  currency: string | null; documentedScale: string; monetaryConsistency: string;
  shippingBrl: number | string | null; ddpIncludesVatTax: boolean | null;
};
export type AssistedFreightPreview = {
  orderId: number; observedAt: string; reviewBefore: string; destinationCoverage: string;
  provinceSent: string | null; commercialAuthorized: boolean;
  lines: Array<{ productId: string; skuId: string; quantity: number; status: string;
    observedUnitPriceBrl: number | null; observedProductsBrl: number | null;
    tax: { priceIncludesTax: boolean | null; receivedTaxAmount: string | null; currency: string | null } | null;
    options: FreightObservedOption[] }>;
  costs: { observedProductsBrl: number | null; supplierTotalBrl: number | null; grossMarginBrl: number | null;
    netMarginBrl: number | null; status: string };
  blockers: string[];
};
export type AssistedLegInput = { code: string; name: string; originCountry: string; shippingBrl: string; estimatedDays: string; skuIds: string[] };

/** Only empty fields are populated. A grouping chosen manually never inherits an individual SKU rate. */
export function prefillObservedLeg(leg: AssistedLegInput, option: FreightObservedOption): AssistedLegInput {
  if (leg.skuIds.length !== 1) return leg;
  if (leg.code && leg.code !== option.code) return leg;
  if (leg.originCountry && option.originCountry && leg.originCountry !== option.originCountry) return leg;
  const rate = option.shippingBrl === null ? null : String(option.shippingBrl);
  return { ...leg,
    code: leg.code || (option.code && /^[A-Za-z0-9_.-]{1,50}$/.test(option.code) ? option.code : ''),
    name: leg.name || option.name || '',
    originCountry: leg.originCountry || option.originCountry || '',
    estimatedDays: leg.estimatedDays || (option.estimatedDays !== null && Number.isInteger(option.estimatedDays)
      && option.estimatedDays >= 1 && option.estimatedDays <= 365 ? String(option.estimatedDays) : ''),
    shippingBrl: leg.shippingBrl || (option.currency === 'BRL' && rate !== null && /^\d{1,8}(?:\.\d{1,2})?$/.test(rate) ? rate : ''),
  };
}

export function prefillUnambiguousLegs(legs: AssistedLegInput[], preview: AssistedFreightPreview): AssistedLegInput[] {
  if (!Number.isFinite(Date.parse(preview.reviewBefore)) || Date.now() >= Date.parse(preview.reviewBefore)) return legs;
  return legs.map(leg => {
    const lines = preview.lines.filter(line => leg.skuIds.length === 1 && line.skuId === leg.skuIds[0]);
    return lines.length === 1 && lines[0].options.length === 1 ? prefillObservedLeg(leg, lines[0].options[0]) : leg;
  });
}
