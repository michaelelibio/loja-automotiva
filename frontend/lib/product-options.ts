import type { ProductVariant } from '@/lib/products';

const normalized = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
const labels: Record<string,string> = {color:'Cor',colour:'Cor',cor:'Cor',size:'Tamanho',tamanho:'Tamanho',model:'Modelo',modelo:'Modelo',style:'Modelo',quantity:'Quantidade',quantidade:'Quantidade',pack:'Quantidade','ships from':'Envio de','ship from':'Envio de','envio de':'Envio de'};
const ids: Record<string,string> = {'14':'Cor','5':'Tamanho','200007763':'Envio de'};
const readable = (value: unknown): value is string => typeof value === 'string' && !!value.trim()
  && !/^\d{6,}$|^\d+:\d+(?:#.*)?$|:(?:name|valueid)$/i.test(value.trim());

function presentedAttributes(variant: ProductVariant) {
  const entries = Object.entries(variant.attributes ?? {});
  const result: Record<string,string> = {};
  let valid=true;
  for (const [key,value] of entries) {
    if (/:(?:name|valueid)$/i.test(key) || !readable(value)) continue;
    const name = entries.find(([candidate]) => candidate.toLowerCase() === `${key}:name`.toLowerCase())?.[1];
    const label = ids[key] ?? (typeof name === 'string' ? labels[normalized(name)] ?? (readable(name) && !/^\d+$/.test(name) ? name.trim() : undefined)
      : labels[normalized(key)] ?? (!/^\d+$|^option\d+$/i.test(key) && readable(key) ? key : undefined));
    if (label) {
      if(result[label] && result[label]!==value.trim())valid=false;
      result[label] = value.trim();
    }
  }
  return {attributes:result,valid};
}

export function customerAttributes(variant:ProductVariant):Record<string,string> {
  const result=presentedAttributes(variant);return result.valid?result.attributes:{};
}

export function variantOptions(variants: ProductVariant[]) {
  let rows = variants.filter(v => /^\d+$/.test(v.id) && v.availableForSale !== false
    && (v.stockQuantity == null || v.stockQuantity > 0)).map(variant => ({variant,...presentedAttributes(variant)})).filter(row=>row.valid);
  if (rows.length > 1) rows = rows.map(row => Object.keys(row.attributes).length || !readable(row.variant.name) || /^\d+$/.test(row.variant.name)
    ? row : {...row,attributes:{Opção:row.variant.name.trim()}});
  const preferred=['Cor','Tamanho','Modelo','Quantidade','Envio de'];
  const rank=(key:string)=>preferred.includes(key)?preferred.indexOf(key):preferred.length;
  const keys = [...new Set(rows.flatMap(row => Object.keys(row.attributes)))].sort((a,b)=>rank(a)-rank(b)||a.localeCompare(b,'pt-BR'));
  rows = rows.filter(row => keys.every(key => !!row.attributes[key]));
  const signature = (row: typeof rows[number]) => JSON.stringify(keys.map(key => row.attributes[key]));
  const counts = new Map<string,number>();
  rows.forEach(row => counts.set(signature(row),(counts.get(signature(row)) ?? 0)+1));
  rows = rows.filter(row => counts.get(signature(row)) === 1);
  const available = rows.filter(row => row.variant.availableForSale !== false && (row.variant.stockQuantity == null || row.variant.stockQuantity > 0));
  const groups = keys.filter(key => new Set(rows.map(row => row.attributes[key])).size > 1);
  return {rows,available,groups,automaticId:available.length === 1 ? available[0].variant.id : ''};
}

export function variantLabel(variant?: ProductVariant): string {
  if (!variant) return '';
  const attributes = customerAttributes(variant);
  return Object.entries(attributes).map(([key,value]) => `${key}: ${value}`).join(' · ')
    || (readable(variant.name) && !/^\d+$/.test(variant.name) ? variant.name : 'Opção única');
}
