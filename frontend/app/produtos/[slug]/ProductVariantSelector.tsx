'use client';

import { useState } from 'react';
import type { ProductVariant } from '@/lib/products';
import { variantOptions } from '@/lib/product-options';

export function ProductVariantSelector({ variants, available, selectedVariantId, onChange }: {
  variants: ProductVariant[]; available: boolean; selectedVariantId: string; onChange: (id:string) => void;
}) {
  const options = variantOptions(variants);
  const [selection,setSelection] = useState<Record<string,string>>({});
  if (!options.available.length) return <p role="status">As opções disponíveis estão sendo atualizadas. Tente novamente mais tarde.</p>;
  if (options.available.length === 1) return <p className="variant-single">Opção selecionada automaticamente.</p>;
  const selected = options.rows.find(row => row.variant.id === selectedVariantId)?.attributes ?? selection;
  function choose(key:string,value:string) {
    const index = options.groups.indexOf(key);
    const next = Object.fromEntries(options.groups.slice(0,index).filter(k => selected[k]).map(k => [k,selected[k]]));
    next[key] = value;
    setSelection(next);
    const matches = options.available.filter(row => Object.entries(next).every(([k,v]) => row.attributes[k] === v));
    onChange(matches.length === 1 ? matches[0].variant.id : '');
  }
  return <div className="product-variant-groups">{options.groups.map((key,index) => <fieldset className="product-variant-group" key={key} disabled={!available}>
    <legend>{key}</legend><div>{[...new Set(options.rows.map(row => row.attributes[key]))].map(value => {
      const possible = options.available.some(row => row.attributes[key] === value
        && options.groups.slice(0,index).every(k => !selected[k] || row.attributes[k] === selected[k]));
      return <button key={value} type="button" disabled={!possible} aria-pressed={selected[key] === value}
        className={selected[key] === value ? 'selected' : ''} onClick={() => choose(key,value)}>{value}</button>;
    })}</div></fieldset>)}</div>;
}
