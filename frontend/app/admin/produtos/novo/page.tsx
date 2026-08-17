'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { AdminProductForm } from '@/components/admin/AdminProductForm';
import { useAuth } from '@/context/AuthContext';
import { AdminProductApiError, createAdminProduct } from '@/lib/api/admin/products';
import type { AdminProductPayload } from '@/lib/types/admin-product';

export default function NewAdminProductPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  async function create(payload: AdminProductPayload) {
    setSubmitting(true); setError(null); setFields({});
    try {
      const product = await createAdminProduct(payload);
      router.replace(`/admin/produtos/${product.id}?created=1`);
    } catch (cause) {
      if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 403) { setError('Acesso negado. Sua conta não possui permissão administrativa.'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 409) {
        const duplicate = cause.message.toLowerCase().includes('sku') ? 'Já existe um produto com este SKU.' : 'Já existe um produto com este slug.';
        setError(duplicate); setFields(cause.message.toLowerCase().includes('sku') ? { sku: duplicate } : { slug: duplicate }); return;
      }
      if (cause instanceof AdminProductApiError && cause.status === 400) { setError('Revise os campos destacados.'); setFields(cause.fields); return; }
      setError('Não foi possível criar o produto. Tente novamente.');
    } finally { setSubmitting(false); }
  }

  return <><header className="admin-page-heading admin-detail-heading"><div><Link href="/admin/produtos">← Produtos</Link><p className="eyebrow">NOVO PRODUTO</p><h1>Cadastrar produto</h1><p>Preencha os dados comerciais e de catálogo.</p></div></header><AdminProductForm submitting={submitting} apiError={error} apiFields={fields} onSubmit={create} /></>;
}
