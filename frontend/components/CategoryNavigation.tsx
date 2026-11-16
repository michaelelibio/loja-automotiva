'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { fetchCategories } from '@/lib/catalog';
import type { Category } from '@/lib/catalog-types';
export function CategoryNavigation() {
  const [categories, setCategories] = useState<Category[]>([]);
  useEffect(() => { let current = true; fetchCategories().then((data) => { if (current) setCategories(data); }).catch(() => {}); return () => { current = false; }; }, []);
  if (!categories.length) return null;
  return <details className="category-navigation" onKeyDown={(event) => { if (event.key === 'Escape') { event.currentTarget.open = false; event.currentTarget.querySelector('summary')?.focus(); } }}><summary>Categorias</summary><nav className="category-navigation-menu" aria-label="Categorias de produtos">{categories.filter((c) => c.parentId === null).map((c) => <div key={c.id}><Link href={`/categorias/${c.path}`}>{c.name}</Link>{categories.filter((s) => s.parentId === c.id).map((s) => <Link key={s.id} href={`/categorias/${s.path}`}>{s.name}</Link>)}</div>)}</nav></details>;
}
