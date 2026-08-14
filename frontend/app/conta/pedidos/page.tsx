'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Header } from '@/components/Header'; import { Footer } from '@/components/Footer'; import { OrdersPanel } from '@/components/OrdersPanel'; import { useAuth } from '@/context/AuthContext';

export default function OrdersPage() {
  const router = useRouter(); const { isAuthenticated, isLoading, sessionError } = useAuth();
  useEffect(() => { if (!isLoading && !sessionError && !isAuthenticated) router.replace('/login'); }, [isLoading, sessionError, isAuthenticated, router]);
  if (isLoading || sessionError || !isAuthenticated) return <main><Header /><div className="account-route-state">{sessionError ?? 'Carregando seus pedidos...'}</div><Footer /></main>;
  return <main><Header /><section className="account-route-intro"><p className="eyebrow">MINHA CONTA</p><h1>Meus pedidos</h1></section><section className="account-route-content"><OrdersPanel /></section><Footer /></main>;
}
