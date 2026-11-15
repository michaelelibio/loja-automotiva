'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminOrderApiError, getAdminAliExpressRecipient, updateAdminAliExpressRecipient, reconcileAdminAliExpressRecipient } from '@/lib/api/admin/orders';
import type { AdminAliExpressRecipient } from '@/lib/types/admin-order';

export function AliExpressRecipientSummary({ value }: { value: AdminAliExpressRecipient }) {
  return <><p>CPF: <strong>{value.maskedCpf ?? 'Não cadastrado'}</strong></p>
    <p>Data de nascimento: <strong>{value.birthDatePresent ? 'Cadastrada' : 'Não cadastrada'}</strong></p>
    <p>{value.complete ? 'Campos obrigatórios preenchidos. Confira se CPF e nascimento pertencem ao destinatário.' : 'Há campos obrigatórios ausentes ou inválidos.'}</p>
    {value.manualRetryReady ? <p role="status">Reconciliação registrada. Envio manual liberado por 15 minutos.</p>
      : value.manualHold && <p role="status">Criação retida para correção e reconciliação manual.</p>}
  </>;
}

export function AliExpressRecipientEditor({ orderId, onUpdated }: { orderId: number; onUpdated: () => void }) {
  const [value, setValue] = useState<AdminAliExpressRecipient | null>(null);
  const [editing, setEditing] = useState(false); const [cpf, setCpf] = useState(''); const [birthDate, setBirthDate] = useState('');
  const [checkedData, setCheckedData] = useState(false); const [checkedHistory, setCheckedHistory] = useState(false);
  const [busy, setBusy] = useState(false); const [feedback, setFeedback] = useState<string | null>(null); const locked = useRef(false);
  const { logout } = useAuth(); const router = useRouter();
  useEffect(() => { let active = true; getAdminAliExpressRecipient(orderId).then(v => { if (active) setValue(v); }).catch(() => {
    if (active) setFeedback('Não foi possível consultar os dados do destinatário.');
  }); return () => { active = false; }; }, [orderId]);
  function failed(cause: unknown) {
    if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); }
    setFeedback(cause instanceof AdminOrderApiError ? cause.message : 'Não foi possível concluir a operação. Atualize os dados.');
  }
  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!value?.canEdit || locked.current) return;
    locked.current = true; setBusy(true); setFeedback(null);
    try { const updated = await updateAdminAliExpressRecipient(orderId, { cpf, birthDate, expectedContext: value.contextDigest });
      setValue(updated); setCpf(''); setBirthDate(''); setEditing(false); setCheckedData(false); setCheckedHistory(false);
      setFeedback('Dados corrigidos somente neste pedido. Confira o histórico antes de liberar uma tentativa manual.'); onUpdated();
    } catch (cause) { failed(cause); }
    finally { locked.current = false; setBusy(false); }
  }
  async function reconcile() {
    if (!value?.canReconcile || value.recipientRevision == null || !checkedData || !checkedHistory || locked.current) return;
    locked.current = true; setBusy(true); setFeedback(null);
    try { setValue(await reconcileAdminAliExpressRecipient(orderId, { expectedContext: value.contextDigest, expectedAttemptCount: value.attemptCount,
      recipientRevision: value.recipientRevision, recipientDataConfirmed: checkedData, allPreviousAttemptsChecked: checkedHistory, noSupplierOrderConfirmed: checkedHistory }));
      setCheckedData(false); setCheckedHistory(false); setFeedback('Reconciliação registrada. Nenhum pedido foi criado.'); onUpdated();
    } catch (cause) { failed(cause); }
    finally { locked.current = false; setBusy(false); }
  }
  async function refresh() {
    if (locked.current) return; locked.current = true; setBusy(true); setFeedback(null);
    try { setValue(await getAdminAliExpressRecipient(orderId)); setCheckedData(false); setCheckedHistory(false); }
    catch (cause) { failed(cause); } finally { locked.current = false; setBusy(false); }
  }
  return <section className="admin-card"><h2>Destinatário AliExpress</h2>
    {value && <AliExpressRecipientSummary value={value} />}
    <p>Correções são salvas somente nos dados de entrega deste pedido. O cadastro geral da cliente permanece separado.</p>
    <button type="button" disabled={busy} onClick={() => void refresh()}>Atualizar dados do destinatário</button>
    {value?.canEdit && !editing && <button type="button" disabled={busy} onClick={() => { setEditing(true); setFeedback(null); }}>Corrigir CPF e nascimento</button>}
    {editing && <form onSubmit={save} autoComplete="off"><label>CPF do destinatário<input name="recipientCpf" type="text" inputMode="numeric" autoComplete="off" maxLength={14} required value={cpf} onChange={e => setCpf(e.target.value)} /></label>
      <label>Nascimento (DD/MM/AAAA)<input name="recipientBirthDate" type="text" inputMode="numeric" autoComplete="off" maxLength={10} placeholder="DD/MM/AAAA" required value={birthDate} onChange={e => setBirthDate(e.target.value)} /></label>
      <p>Informe os dois dados conferidos. Salvar mantém a criação retida para reconciliação.</p>
      <button type="button" disabled={busy} onClick={() => { setCpf(''); setBirthDate(''); setEditing(false); }}>Cancelar correção</button>
      <button type="submit" disabled={busy || !cpf.trim() || !birthDate.trim()}>{busy ? 'Salvando...' : 'Salvar dados neste pedido'}</button>
    </form>}
    {value?.manualHold && !value.manualRetryReady && !editing && <div>
      <p>Confira toda a conta AliExpress, todas as páginas e o período de cada tentativa anterior, inclusive com os dados anteriores à correção. Se houver pedido correspondente ou dúvida, mantenha o bloqueio.</p>
      {value.lastAttemptAt && <p>Última tentativa: {new Date(value.lastAttemptAt).toLocaleString('pt-BR')}.</p>}
      <label><input type="checkbox" checked={checkedData} disabled={busy || !value.canReconcile} onChange={e => setCheckedData(e.target.checked)} />Conferi que CPF e nascimento pertencem ao destinatário.</label>
      <label><input type="checkbox" checked={checkedHistory} disabled={busy || !value.canReconcile} onChange={e => setCheckedHistory(e.target.checked)} />Conferi o histórico completo após todas as tentativas; não existe pedido correspondente.</label>
      <button type="button" disabled={busy || !value.canReconcile || !checkedData || !checkedHistory} onClick={() => void reconcile()}>Registrar reconciliação e liberar envio manual</button>
      {!value.canReconcile && <p>Atualize os dados após o término da tentativa anterior e confira os campos obrigatórios.</p>}
    </div>}
    {feedback && <p role="status">{feedback}</p>}
    <small>Salvar ou reconciliar não cria pedidos e não paga o AliExpress.</small>
  </section>;
}
