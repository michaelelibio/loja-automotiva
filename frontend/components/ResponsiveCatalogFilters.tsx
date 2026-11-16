'use client';
import { useState, type ReactNode } from 'react';
export function ResponsiveCatalogFilters({ children }: { children: ReactNode }) {
  const [expanded, setExpanded] = useState(false);
  return <aside className={`catalog-filter-panel${expanded ? ' expanded' : ''}`}>
    <h2 className="catalog-filter-title">Filtrar produtos</h2>
    <button type="button" className="catalog-filter-toggle" aria-expanded={expanded} aria-controls="catalog-filter-content" onClick={() => setExpanded((value) => !value)}>{expanded ? 'Fechar filtros' : 'Filtrar produtos'} <span aria-hidden="true">{expanded ? '−' : '+'}</span></button>
    <div id="catalog-filter-content">{children}</div>
  </aside>;
}
