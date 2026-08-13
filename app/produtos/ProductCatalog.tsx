'use client';

import { useState } from 'react';
import { ProductCard } from '@/components/ProductCard';
import type { Product } from '@/lib/products';
import { normalizeText } from '@/lib/text';

type PriceFilter = 'all' | 'under-50' | '50-100' | 'over-100';
type SortOption = 'featured' | 'lowest' | 'highest';
type ProductTypeFilter = 'all' | Product['productType'];

const categories = ['Todas', 'Lavagem', 'Proteção', 'Detalhamento', 'Acessórios'] as const;

export function ProductCatalog({ products }: { products: Product[] }) {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<(typeof categories)[number]>('Todas');
  const [price, setPrice] = useState<PriceFilter>('all');
  const [productType, setProductType] = useState<ProductTypeFilter>('all');
  const [sort, setSort] = useState<SortOption>('featured');

  const filteredProducts = products
    .filter((product) => {
      const normalizedSearch = normalizeText(search);
      const searchableText = normalizeText(`${product.name} ${product.category} ${product.description}`);
      const searchTerms = normalizedSearch.split(' ').filter(Boolean);
      const matchesSearch = searchTerms.every((term) => searchableText.includes(term));
      const matchesCategory = category === 'Todas' || normalizeText(product.category) === normalizeText(category);
      const matchesType = productType === 'all' || product.productType === productType;
      const matchesPrice = price === 'all'
        || (price === 'under-50' && product.price < 50)
        || (price === '50-100' && product.price >= 50 && product.price <= 100)
        || (price === 'over-100' && product.price > 100);

      return matchesSearch && matchesCategory && matchesType && matchesPrice;
    })
    .sort((first, second) => {
      if (sort === 'lowest') return first.price - second.price;
      if (sort === 'highest') return second.price - first.price;
      return Number(second.featured) - Number(first.featured);
    });

  return (
    <section className="catalog-section" aria-label="Catálogo de produtos">
      <div className="catalog-toolbar">
        <label className="catalog-search">
          <span>Buscar produtos</span>
          <input value={search} onChange={(event) => setSearch(event.target.value)} type="search" placeholder="Nome do produto" />
        </label>
        <label className="catalog-select">
          <span>Categoria</span>
          <select value={category} onChange={(event) => setCategory(event.target.value as (typeof categories)[number])}>
            {categories.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <label className="catalog-select">
          <span>Tipo</span>
          <select value={productType} onChange={(event) => setProductType(event.target.value as ProductTypeFilter)}>
            <option value="all">Todos</option>
            <option value="SINGLE">Produtos</option>
            <option value="KIT">Kits</option>
          </select>
        </label>
        <label className="catalog-select">
          <span>Faixa de preço</span>
          <select value={price} onChange={(event) => setPrice(event.target.value as PriceFilter)}>
            <option value="all">Todos os preços</option>
            <option value="under-50">Até R$ 49,90</option>
            <option value="50-100">R$ 50 a R$ 100</option>
            <option value="over-100">Acima de R$ 100</option>
          </select>
        </label>
        <label className="catalog-select">
          <span>Ordenar por</span>
          <select value={sort} onChange={(event) => setSort(event.target.value as SortOption)}>
            <option value="featured">Destaques</option>
            <option value="lowest">Menor preço</option>
            <option value="highest">Maior preço</option>
          </select>
        </label>
      </div>

      <div className="catalog-results-heading">
        <p className="eyebrow">Nossa seleção</p>
        <span>{filteredProducts.length} {filteredProducts.length === 1 ? 'produto encontrado' : 'produtos encontrados'}</span>
      </div>

      {filteredProducts.length > 0 ? (
        <div className="products-grid catalog-grid">
          {filteredProducts.map((product) => <ProductCard key={product.id} product={product} />)}
        </div>
      ) : (
        <div className="empty-catalog"><h2>Nenhum produto encontrado</h2><p>Tente buscar por outro nome ou ajustar os filtros.</p></div>
      )}
    </section>
  );
}
