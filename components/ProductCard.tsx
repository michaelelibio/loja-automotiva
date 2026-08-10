import { ArrowUpRightIcon } from './Icons';

export type Product = {
  name: string;
  category: string;
  price: string;
  oldPrice?: string;
  accent: string;
  image: string;
};

export function ProductCard({ product }: { product: Product }) {
  return (
    <article className="product-card">
      <div className="product-image" style={{ backgroundImage: `url(${product.image})`, backgroundColor: product.accent }}>
        <span className="product-category">{product.category}</span>
        <button type="button" className="product-link" aria-label={`Ver ${product.name}`} title={`Ver ${product.name}`}>
          <ArrowUpRightIcon />
        </button>
      </div>
      <div className="product-info">
        <h3>{product.name}</h3>
        <div className="price-row">
          <strong>{product.price}</strong>
          {product.oldPrice && <span>{product.oldPrice}</span>}
        </div>
      </div>
    </article>
  );
}