import { ArrowUpRightIcon } from './Icons';
import type { Product } from '@/data/products';
import Link from 'next/link';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function ProductCard({ product }: { product: Product }) {
  return (
    <Link className="product-card" href={`/produtos/${product.slug}`}>
      <div className="product-image" style={{ backgroundImage: `url(${product.image})`, backgroundColor: product.accent }}>
        <span className="product-category">{product.category}</span>
        <span className="product-link" aria-hidden="true">
          <ArrowUpRightIcon />
        </span>
      </div>
      <div className="product-info">
        <h3>{product.name}</h3>
        <div className="price-row">
          <strong>{currency.format(product.price)}</strong>
          {product.oldPrice && <span>{currency.format(product.oldPrice)}</span>}
        </div>
      </div>
    </Link>
  );
}