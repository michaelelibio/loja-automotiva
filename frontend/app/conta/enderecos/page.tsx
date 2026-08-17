'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AddressesPanel } from '@/components/AddressesPanel';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { useAuth } from '@/context/AuthContext';

export default function AddressesPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading, sessionError } = useAuth();

  useEffect(() => {
    if (!isLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [isAuthenticated, isLoading, sessionError, router]);

  if (isLoading || sessionError || !isAuthenticated) {
    return <main><Header /><div className="account-route-state">{sessionError ?? 'Carregando seus endereços...'}</div><Footer /></main>;
  }

  return <main><Header /><section className="account-route-intro"><div><p className="eyebrow">MINHA CONTA</p><h1>Endereços</h1><p>Gerencie seus endereços de entrega.</p></div></section><section className="account-route-content"><AddressesPanel /></section><Footer /></main>;
}
