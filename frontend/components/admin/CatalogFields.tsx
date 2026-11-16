'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getCategories } from '@/lib/api/admin/catalog';
import type { CatalogInput, Category } from '@/lib/catalog-types';
export function CatalogFields({ value, onChange, errors = {} }: { value: CatalogInput; onChange: (value: CatalogInput) => void; errors?: Record<string, string> }) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => { let current = true; getCategories().then((data) => { if (current) { setCategories(data); setFailed(false); } }).catch(() => { if (current) setFailed(true); }); return () => { current = false; }; }, [retry]);
  function set<K extends keyof CatalogInput>(key: K, next: CatalogInput[K]) { onChange({ ...value, [key]: next }); }
  const flag = (key: 'concentrated' | 'refill', title: string) => <label>{title}<select value={value[key] == null ? '' : String(value[key])} onChange={(e) => set(key, e.target.value === '' ? null : e.target.value === 'true')}><option value="">Não informado</option><option value="true">Sim</option><option value="false">Não</option></select></label>;
  return <section className="admin-card"><div className="admin-form-section-heading"><div><p className="eyebrow">ESTÉTICA AUTOMOTIVA</p><h2>Classificação e vitrines</h2></div><Link href="/admin/categorias">Gerenciar categorias</Link></div><p className="admin-muted">Cadastre somente informações confirmadas. Deixe em branco quando não houver informação técnica.</p>
    {failed && <p role="alert">Não foi possível carregar categorias. <button type="button" onClick={() => setRetry((n) => n + 1)}>Tentar novamente</button></p>}
    {Object.keys(errors).some((k) => k.startsWith('catalog.')) && <ul role="alert">{Object.entries(errors).filter(([key]) => key.startsWith('catalog.')).map(([key,message]) => <li key={key}>{key.slice(8)}: {message}</li>)}</ul>}
    <div className="admin-product-form-grid">
      <label>Categoria principal<select value={value.categoryId ?? ''} onChange={(e) => onChange({ ...value, categoryId: Number(e.target.value) || null, subcategoryId: null })}><option value="">Sem classificação</option>{categories.filter((c) => c.parentId === null).map((c) => <option key={c.id} value={c.id}>{c.name}{!c.active ? ' (inativa)' : ''}</option>)}</select></label>
      <label>Subcategoria<select value={value.subcategoryId ?? ''} onChange={(e) => set('subcategoryId', Number(e.target.value) || null)} disabled={!value.categoryId}><option value="">Sem subcategoria</option>{categories.filter((c) => c.parentId === value.categoryId).map((c) => <option key={c.id} value={c.id}>{c.name}{!c.active ? ' (inativa)' : ''}</option>)}</select></label>
      <label>Marca<input maxLength={100} value={value.brand ?? ''} onChange={(e) => set('brand', e.target.value || null)} /></label>
      <label>Uso<select value={value.usage ?? ''} onChange={(e) => set('usage', (e.target.value || null) as CatalogInput['usage'])}><option value="">Não informado</option><option value="DOMESTIC">Doméstico</option><option value="PROFESSIONAL">Profissional</option><option value="BOTH">Ambos</option></select></label>
      {(['surfaces', 'purposes', 'searchTerms'] as const).map((key) => <label className="wide" key={key}>{({ surfaces: 'Superfícies compatíveis', purposes: 'Finalidades', searchTerms: 'Termos de busca' })[key]}<input value={value[key].join(',')} onChange={(e) => set(key, e.target.value.split(','))} /><small>Separe por vírgulas. Até {key === 'searchTerms' ? 40 : 30} itens, com 100 caracteres cada.</small></label>)}
      {flag('concentrated','Concentrado')}{flag('refill','Refil')}
      <label>Seleção comercial<select value={value.commercialOrigin ?? ''} onChange={(e) => set('commercialOrigin', (e.target.value || null) as CatalogInput['commercialOrigin'])}><option value="">Sem classificação</option><option value="NATIONAL">Nacional — seleção AliExpress</option><option value="INTERNATIONAL">Internacional — seleção CJ</option></select><small>Classificação comercial; não representa país de fabricação ou origem de envio.</small></label>
      <div className="catalog-checks"><label><input type="checkbox" checked={value.featured} onChange={(e) => set('featured',e.target.checked)} />Produto em destaque</label><label><input type="checkbox" checked={value.beginner} onChange={(e) => set('beginner',e.target.checked)} />Para começar</label></div>
    </div>
  </section>;
}
