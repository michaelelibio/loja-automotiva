'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { AdminOrderApiError, getAdminAliExpressManualData } from '@/lib/api/admin/orders';
import type { AdminAliExpressManualData } from '@/lib/types/admin-order';

export function AliExpressManualOrderData({ orderId }: { orderId: number }) {
  const { user } = useAuth();
  return <ManualDataPanel key={`${orderId}:${user?.id ?? 'guest'}`} orderId={orderId} />;
}

export function ManualDataPanel({ orderId }: { orderId: number }) {
  const [data, setData] = useState<AdminAliExpressManualData | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sequence = useRef(0); const locked = useRef(false);
  const { logout } = useAuth();

  const cancelPending = useCallback(() => { sequence.current++; locked.current = false; }, []);
  const hide = useCallback(() => { cancelPending(); setData(null); setBusy(false); setError(null); }, [cancelPending]);
  useEffect(() => {
    const conceal = () => { sequence.current++; locked.current = false; setData(null); setBusy(false); };
    const visibility = () => { if (document.hidden) conceal(); };
    window.addEventListener('blur', conceal); document.addEventListener('visibilitychange', visibility);
    return () => { cancelPending(); window.removeEventListener('blur', conceal); document.removeEventListener('visibilitychange', visibility); };
  }, [cancelPending]);
  useEffect(() => { if (!data) return; const timer = window.setTimeout(hide, 90000); return () => window.clearTimeout(timer); }, [data, hide]);

  async function reveal() {
    if (locked.current) return;
    locked.current = true; const current = ++sequence.current; setBusy(true); setError(null);
    try { const result = await getAdminAliExpressManualData(orderId); if (current === sequence.current) setData(result); }
    catch (cause) {
      if (current !== sequence.current) return;
      setData(null); setError('Não foi possível consultar os dados protegidos. Confira sua sessão e tente novamente.');
      if (cause instanceof AdminOrderApiError && cause.status === 401) logout();
    } finally { if (current === sequence.current) { locked.current = false; setBusy(false); } }
  }

  return <section className="admin-card admin-manual-order-data">
    <h2>Dados para preenchimento no AliExpress</h2>
    <p>Consulte o snapshot deste pedido para preencher a compra manualmente. Confira o histórico completo do fornecedor antes de criar qualquer pedido; uma resposta de erro não comprova ausência de compra.</p>
    <p>CPF e nascimento devem pertencer à mesma pessoa indicada como destinatária. A consulta não corrige dados, não libera reenvio e não cria ou paga pedidos.</p>
    <p>Se a automação estiver pendente ou em andamento, aguarde sua conclusão. Nunca crie uma compra manual em paralelo.</p>
    <button type="button" disabled={busy} aria-expanded={!!data} aria-controls="manual-order-details" onClick={data ? hide : () => void reveal()}>{busy ? 'Consultando…' : data ? 'Ocultar dados pessoais' : 'Exibir dados protegidos deste pedido'}</button>
    {error && <p role="alert">{error}</p>}
    {data && <div id="manual-order-details">
      <p role="status">Visíveis por até 90 segundos. Os dados serão ocultados ao sair desta aba.</p>
      {!data.complete && <p role="alert">Há dados ausentes ou inválidos: {data.pendingFields.join(', ')}. Confirme os dados antes de comprar.</p>}
      {data.supplierOrderIds.length > 0 && <p role="alert">Já existe referência no fornecedor: {data.supplierOrderIds.join(', ')}. Não crie outro pedido.</p>}
      <dl className="admin-manual-fields">
        {Object.entries({ Destinatário: data.recipientName, CPF: data.cpf, 'Nascimento (DD/MM/AAAA)': data.birthDate,
          Telefone: data.phone ? `${data.phoneCountry} ${data.phone}` : null, País: data.country, CEP: data.zipCode,
          Logradouro: data.street, Número: data.number, Complemento: data.complement, Bairro: data.neighborhood,
          Cidade: data.city, Estado: data.state }).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || 'Não cadastrado'}</dd></div>)}
      </dl>
      <h3>Itens e SKUs do pedido</h3>
      {data.items.map((item, index) => <article key={index}>
        <strong>{item.productName}</strong><p>Variante: {item.variantName || 'Sem descrição no snapshot; confira o SKU original.'}</p>
        <p>Quantidade: {item.quantity} · Produto AliExpress: {item.supplierProductId || 'Não cadastrado'}</p>
        <p>SKU AliExpress: {item.supplierVariantId || 'Não cadastrado'}{item.supplierSku ? ` · Referência: ${item.supplierSku}` : ''}</p>
        {item.supplierProductId && /^[0-9]{8,30}$/.test(item.supplierProductId) && <a href={`https://www.aliexpress.com/item/${item.supplierProductId}.html`} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Abrir produto no AliExpress ↗</a>}
      </article>)}
      <p>Escolha a variante correspondente ao SKU e confira o custo atual no AliExpress. Depois de uma criação manual, guarde o número do fornecedor e reconcilie o vínculo na inGarage antes de qualquer reenvio. O pagamento ao fornecedor permanece manual.</p>
    </div>}
  </section>;
}
