import type { Product } from '@/lib/products';

type Item = { productId: string; variantId?: string; quantity: number };

export function reconcileCartItems<T extends Item>(items: T[], products: Product[]): T[] {
  const byId = new Map(products.map((product) => [product.id, product]));
  return items.filter((item) => {
    const product = byId.get(item.productId);
    if (!product || !product.availableForSale) return false;
    // Never silently replace a removed SKU with another variant of the product.
    return item.variantId ? product.variants.some((variant) => variant.id === item.variantId)
      : !product.requiresVariantSelection;
  });
}
