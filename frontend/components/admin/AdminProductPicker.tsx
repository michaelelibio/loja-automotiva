'use client';

import { useEffect, useState } from 'react';
import { AdminProductApiError, getAdminProducts } from '@/lib/api/admin/products';
import type { AdminProduct } from '@/lib/types/admin-product';

export function AdminProductPicker({ id, value, onChange, onAuthorizationError, allowClear = false, localStockOnly = false }: {
  id: string;
  value: AdminProduct | null;
  onChange: (product: AdminProduct | null) => void;
  onAuthorizationError: (status: 401 | 403) => void;
  allowClear?: boolean;
  localStockOnly?: boolean;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<AdminProduct[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let current = true;
    const timer = window.setTimeout(() => {
      getAdminProducts({ search: query.trim() || undefined, page: 0, size: 20 }).then((data) => {
        if (current) setResults(localStockOnly ? data.content.filter((product) => product.fulfillmentType === 'LOCAL_STOCK') : data.content);
      }).catch((cause: unknown) => {
        if (!current) return;
        if (cause instanceof AdminProductApiError && (cause.status === 401 || cause.status === 403)) onAuthorizationError(cause.status);
        else setError('Não foi possível buscar produtos.');
      }).finally(() => { if (current) setLoading(false); });
    }, 250);
    return () => { current = false; window.clearTimeout(timer); };
  }, [localStockOnly, onAuthorizationError, open, query]);

  return <div className="admin-product-picker">
    <div className="admin-product-picker-control">
      <input id={id} type="search" autoComplete="off" value={open ? query : value ? `${value.name} · ${value.sku}` : ''} placeholder="Buscar por nome ou SKU" onFocus={() => { setOpen(true); setLoading(true); setError(null); }} onChange={(event) => { setQuery(event.target.value); setOpen(true); setLoading(true); setError(null); }} />
      {allowClear && value && <button type="button" aria-label="Limpar produto" onClick={() => { onChange(null); setQuery(''); }}>×</button>}
    </div>
    {open && <div className="admin-product-picker-results">{loading && <span>Buscando…</span>}{error && <span className="error">{error}</span>}{!loading && !error && (results.length ? results.map((product) => <button type="button" key={product.id} onClick={() => { onChange(product); setQuery(''); setOpen(false); }}><strong>{product.name}</strong><span>SKU {product.sku} · {product.fulfillmentType === 'DROPSHIPPING' ? 'dropshipping' : `estoque ${product.stock}`}</span></button>) : <span>Nenhum produto encontrado.</span>)}</div>}
  </div>;
}
