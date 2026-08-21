'use client';

import { useMemo, useState } from 'react';
import type { ProductVariant } from '@/lib/products';

function semanticKeys(variants: ProductVariant[]): string[] | null {
  if (variants.length === 0) return null;
  const keys = Object.keys(variants[0].attributes);
  if (keys.length === 0 || keys.some((key) => /^option\d+$/i.test(key))) return null;
  if (!variants.every((variant) => {
    const current = Object.keys(variant.attributes);
    return current.length === keys.length && keys.every((key) => current.includes(key)
      && variant.attributes[key].trim().length > 0);
  })) return null;
  const combinations = new Set(variants.map((variant) => keys
    .map((key) => variant.attributes[key]).join('\u0000')));
  return combinations.size === variants.length ? keys : null;
}

export function ProductVariantSelector({ variants, available, selectedVariantId, onChange }: {
  variants: ProductVariant[];
  available: boolean;
  selectedVariantId: string;
  onChange: (variantId: string) => void;
}) {
  const keys = useMemo(() => semanticKeys(variants), [variants]);
  const initialVariant = variants.length === 1 ? variants[0] : undefined;
  const [selection, setSelection] = useState<Record<string, string>>(
    () => initialVariant?.attributes ?? {});

  if (!keys) {
    return <label className="variant-selector">
      <span>Escolha uma opção</span>
      <select value={selectedVariantId} onChange={(event) => onChange(event.target.value)} disabled={!available}>
        <option value="">Selecione</option>
        {variants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}
      </select>
    </label>;
  }

  const valuesByKey = new Map(keys.map((key) => [key,
    Array.from(new Set(variants.map((variant) => variant.attributes[key])))]));
  const optionAvailable = (key: string, value: string) => {
    const keyIndex = keys.indexOf(key);
    return variants.some((variant) => variant.attributes[key] === value
      && keys.slice(0, keyIndex).every((previousKey) => !selection[previousKey]
        || variant.attributes[previousKey] === selection[previousKey]));
  };

  const choose = (key: string, value: string) => {
    const keyIndex = keys.indexOf(key);
    const next = Object.fromEntries(Object.entries(selection)
      .filter(([selectedKey]) => keys.indexOf(selectedKey) < keyIndex));
    next[key] = value;
    setSelection(next);
    const variant = keys.length === Object.keys(next).length
      ? variants.find((candidate) => keys.every((attribute) => candidate.attributes[attribute] === next[attribute]))
      : undefined;
    onChange(variant?.id ?? '');
  };

  return <div className="product-variant-groups">
    {keys.map((key) => <fieldset className="product-variant-group" key={key} disabled={!available}>
      <legend>{key}</legend>
      <div>{valuesByKey.get(key)?.map((value) => {
        const canSelect = optionAvailable(key, value);
        const selected = selection[key] === value;
        return <button key={value} type="button" className={selected ? 'selected' : ''}
          disabled={!canSelect || !available} aria-pressed={selected}
          onClick={() => choose(key, value)}>{value}</button>;
      })}</div>
    </fieldset>)}
  </div>;
}
