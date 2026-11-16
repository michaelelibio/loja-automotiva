'use client';
import { useEffect, useState } from 'react';
import { getCategories, saveCategory, type CategoryInput } from '@/lib/api/admin/catalog';
import type { Category } from '@/lib/catalog-types';
const initial: CategoryInput = { name: '', slug: '', description: '', parentId: null, position: 0, active: true };
export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [value, setValue] = useState<CategoryInput>({ ...initial });
  const [editing, setEditing] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => { let current = true; getCategories().then((data) => { if (current) setCategories(data); }).catch((e) => { if (current) setMessage(e instanceof Error ? e.message : 'Falha ao carregar.'); }).finally(() => { if (current) setLoading(false); }); return () => { current = false; }; }, [retry]);
  const ordered = categories.filter((c) => c.parentId === null).flatMap((c) => [c, ...categories.filter((s) => s.parentId === c.id)]);
  return <><header className="admin-page-heading"><div><p className="eyebrow">CATÁLOGO</p><h1>Categorias</h1><p>Ordene e organize a navegação. Categorias sem produtos disponíveis ficam ocultas na loja.</p></div></header>
    {loading && <p role="status">Carregando categorias…</p>}{message && <p role="status">{message}</p>}
    <button type="button" disabled={busy} onClick={() => { setLoading(true); setMessage(''); setRetry((n) => n + 1); }}>Atualizar lista</button>
    <form className="admin-card admin-product-form" onSubmit={async (e) => { e.preventDefault(); if (busy) return; setBusy(true); setMessage(''); try { await saveCategory(editing, value); setEditing(null); setValue({ ...initial }); setRetry((n) => n + 1); setMessage('Categoria salva.'); } catch (error) { setMessage(error instanceof Error ? error.message : 'Falha ao salvar.'); } finally { setBusy(false); } }}>
      <h2>{editing ? 'Editar categoria' : 'Nova categoria'}</h2><div className="admin-product-form-grid">
        <label>Nome<input required maxLength={120} value={value.name} onChange={(e) => setValue({ ...value, name: e.target.value })} /></label>
        <label>Slug da URL<input required maxLength={120} pattern="[a-z0-9]+(-[a-z0-9]+)*" disabled={editing !== null} value={value.slug} onChange={(e) => setValue({ ...value, slug: e.target.value })} /></label>
        <label>Categoria principal<select disabled={editing !== null} value={value.parentId ?? ''} onChange={(e) => setValue({ ...value, parentId: Number(e.target.value) || null })}><option value="">É uma categoria principal</option>{categories.filter((c) => c.parentId === null).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
        <label>Ordem<input type="number" min="0" step="1" required value={value.position} onChange={(e) => setValue({ ...value, position: Number(e.target.value) })} /></label>
        <label className="wide">Descrição<textarea maxLength={1500} value={value.description ?? ''} onChange={(e) => setValue({ ...value, description: e.target.value })} /></label>
        <label className="catalog-checkbox"><input type="checkbox" checked={value.active} onChange={(e) => setValue({ ...value, active: e.target.checked })} />Ativa</label>
      </div><p className="admin-muted">A URL e a categoria principal ficam fixas após a criação. Desativar oculta a navegação e mantém os produtos vinculados.</p><button type="submit" disabled={busy}>{busy ? 'Salvando…' : 'Salvar categoria'}</button> {editing && <button type="button" disabled={busy} onClick={() => { setEditing(null); setValue({ ...initial }); }}>Cancelar edição</button>}
    </form>
    <div className="admin-card catalog-category-list">{ordered.map((c) => <article key={c.id} className={c.parentId ? 'subcategory' : ''}><div><strong>{c.name}</strong><small>/categorias/{c.path}</small></div><span>Ordem {c.position} · {c.active ? 'Ativa' : 'Inativa'}</span><button type="button" disabled={busy} onClick={() => { setEditing(c.id); setValue({ name: c.name, slug: c.slug, description: c.description, parentId: c.parentId, position: c.position, active: c.active }); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>Editar</button></article>)}</div>
  </>;
}
