import type { Product } from '@/lib/products';
import { variantOptions } from '@/lib/product-options';

type Item = { productId: string; variantId?: string; quantity: number };

export function reconcileCartItems<T extends Item>(items: T[], products: Product[]): T[] {
  const byId = new Map(products.map((product) => [product.id, product]));
  return items.filter((item) => {
    const product = byId.get(item.productId);
    if (!product || !product.availableForSale) return false;
    // Never silently replace a removed SKU with another variant of the product.
    return item.variantId ? variantOptions(product.variants).available.some(({variant}) => variant.id === item.variantId)
      : !product.requiresVariantSelection;
  });
}
