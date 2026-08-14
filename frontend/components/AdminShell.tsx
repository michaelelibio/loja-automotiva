'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { useAuth } from '@/context/AuthContext';
import { hasRole } from '@/lib/auth/roles';

export function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isLoading, sessionError } = useAuth();
  const isAdmin = isAuthenticated && hasRole('ADMIN');

  useEffect(() => { if (!isLoading && !sessionError && !isAuthenticated) router.replace('/login'); }, [isAuthenticated, isLoading, sessionError, router]);

  if (isLoading || sessionError || !isAuthenticated) return <main><Header /><div className="admin-route-state">{sessionError ?? 'Verificando acesso...'}</div><Footer /></main>;
  if (!isAdmin) return <main><Header /><section className="admin-route-state"><p className="eyebrow">ACESSO RESTRITO</p><h1>Acesso negado</h1><p>Esta área é exclusiva para administradores da GARAGE.</p><Link href="/">Voltar para a loja</Link></section><Footer /></main>;

  return <main className="admin-area"><Header /><div className="admin-frame"><aside className="admin-sidebar"><Link className="admin-brand" href="/admin"><span>GARAGE</span><small>OPERAÇÃO</small></Link><nav aria-label="Navegação administrativa"><Link className={pathname === '/admin' ? 'active' : ''} href="/admin">Visão geral</Link><Link className={pathname.startsWith('/admin/pedidos') ? 'active' : ''} href="/admin/pedidos">Pedidos</Link></nav><Link className="admin-store-link" href="/">← Voltar à loja</Link></aside><div className="admin-workspace">{children}</div></div><Footer /></main>;
}
