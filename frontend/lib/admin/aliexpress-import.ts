import type { AliExpressImportRequest, AliExpressPreview } from '@/lib/types/admin-aliexpress';

export const ALIEXPRESS_CATEGORIES = ['Lavagem', 'Proteção', 'Detalhamento', 'Acessórios'] as const;

export function prepareAliExpressImport(product: AliExpressPreview, name: string, category: string,
  selected: string[], prices: Record<string, string>): { payload: AliExpressImportRequest | null; error: string | null } {
  const invalid = (error: string) => ({ payload: null, error });
  if (!name.trim() || name.trim().length > 150) return invalid('Informe um nome de até 150 caracteres.');
  if (!ALIEXPRESS_CATEGORIES.some((item) => item === category)) return invalid('Selecione uma categoria da loja.');
  if (!selected.length) return invalid('Selecione pelo menos uma variante.');
  const variantSalePricesBrl: Record<string, string> = {};
  let commonPrice: string | null = null;
  for (const id of selected) {
    if (!product.variants.some((variant) => variant.skuId === id)) return invalid('Consulte novamente as variantes.');
    const value = (prices[id] ?? '').trim().replace(',', '.');
    if (!/^\d{1,10}(?:\.\d{1,2})?$/.test(value) || Number(value) <= 0) return invalid('Defina um preço de venda positivo, com até duas casas decimais, para cada variante selecionada.');
    // Decimal strings avoid using supplier costs or floating point arithmetic in the payload.
    const [integer, fraction = ''] = value.split('.');
    const normalized = `${integer.replace(/^0+(?=\d)/, '')}.${fraction.padEnd(2, '0')}`;
    if (commonPrice !== null && normalized !== commonPrice) return invalid('As variantes selecionadas devem ter o mesmo preço de venda, conforme o catálogo atual.');
    commonPrice = normalized;
    variantSalePricesBrl[id] = normalized;
  }
  if (!product.fingerprint || !/^[a-f0-9]{64}$/.test(product.fingerprint)) return invalid('Consulte novamente o produto antes de importar.');
  return { error: null, payload: { input: product.productId, shipToCountry: 'BR', targetCurrency: 'BRL', targetLanguage: 'pt_BR',
    fingerprint: product.fingerprint, name: name.trim(), category, variantSalePricesBrl } };
}
