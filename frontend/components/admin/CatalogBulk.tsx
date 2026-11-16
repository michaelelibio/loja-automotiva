'use client';
import { useState } from 'react';
import { CatalogFields } from './CatalogFields';
import { emptyCatalog } from '@/lib/catalog-types';
import { bulkClassify } from '@/lib/api/admin/catalog';
export function CatalogBulk({ ids, onSaved, onClear }: { ids: number[]; onSaved: () => void; onClear: () => void }) {
  const [value, setValue] = useState({ ...emptyCatalog });
  const [fields, setFields] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const choices = { taxonomy: 'Categoria e subcategoria', brand: 'Marca', surfaces: 'Superfícies', purposes: 'Finalidades', searchTerms: 'Termos de busca', usage: 'Uso', concentrated: 'Concentrado', refill: 'Refil', featured: 'Destaque', beginner: 'Para começar', commercialOrigin: 'Seleção comercial' };
  if (!ids.length) return null;
  return <details className="admin-card catalog-bulk"><summary>Classificar {ids.length} produto(s) selecionado(s)</summary><form onSubmit={async (e) => { e.preventDefault(); if (busy || !fields.length) return; setBusy(true); setMessage(''); try { await bulkClassify(ids, fields, value); setMessage('Classificação salva.'); onSaved(); } catch (cause) { setMessage(cause instanceof Error ? cause.message : 'Falha ao salvar.'); } finally { setBusy(false); } }}>
    <p>IDs selecionados: {ids.join(', ')}. Apenas os campos marcados abaixo serão substituídos. Valores vazios removem a informação desses campos.</p><div className="catalog-checks">{Object.entries(choices).map(([key,label]) => <label key={key}><input type="checkbox" checked={fields.includes(key)} onChange={(e) => setFields((f) => e.target.checked ? [...f,key] : f.filter((v) => v !== key))} />{label}</label>)}</div>
    <CatalogFields value={value} onChange={setValue} /><button type="submit" disabled={busy || !fields.length}>{busy ? 'Salvando…' : 'Aplicar aos selecionados'}</button> <button type="button" disabled={busy} onClick={onClear}>Limpar seleção</button>{message && <p role="status">{message}</p>}
  </form></details>;
}
