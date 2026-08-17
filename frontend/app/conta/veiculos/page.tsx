'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { VehiclesPanel } from '@/components/VehiclesPanel';
import { useAuth } from '@/context/AuthContext';

export default function VehiclesPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading, sessionError } = useAuth();

  useEffect(() => {
    if (!isLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [isAuthenticated, isLoading, sessionError, router]);

  if (isLoading || sessionError || !isAuthenticated) {
    return <main><Header /><div className="account-route-state">{sessionError ?? 'Carregando seus veículos...'}</div><Footer /></main>;
  }

  return <main><Header /><section className="account-route-intro"><div><p className="eyebrow">MINHA CONTA</p><h1>Veículos</h1><p>Organize os veículos da sua garagem.</p></div></section><section className="account-route-content"><VehiclesPanel /></section><Footer /></main>;
}
