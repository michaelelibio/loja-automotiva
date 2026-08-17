'use client';

import { useCallback, useEffect, useRef, useState, type FormEvent, type InputHTMLAttributes } from 'react';
import * as api from '@/lib/api/addresses';
import type { Address, AddressRequest } from '@/lib/types/address';

type Form = { label: string; recipientName: string; zipCode: string; street: string; number: string; complement: string; neighborhood: string; city: string; state: string; isPrimary: boolean };
const empty: Form = { label: '', recipientName: '', zipCode: '', street: '', number: '', complement: '', neighborhood: '', city: '', state: '', isPrimary: false };
const digits = (value: string) => value.replace(/\D/g, '').slice(0, 8);
const formatZip = (value: string) => { const clean = digits(value); return clean.length > 5 ? `${clean.slice(0, 5)}-${clean.slice(5)}` : clean; };

export function AddressesPanel() {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Address | null>(null);
  const [form, setForm] = useState<Form>(empty);
  const [formError, setFormError] = useState<string | null>(null);
  const [cepMessage, setCepMessage] = useState<string | null>(null);
  const [cepLoading, setCepLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [processing, setProcessing] = useState<number | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const lastCep = useRef('');
  const numberRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setAddresses(await api.getAddresses()); }
    catch { setError('Não foi possível carregar seus endereços.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    let active = true;
    api.getAddresses().then((data) => { if (active) setAddresses(data); })
      .catch(() => { if (active) setError('Não foi possível carregar seus endereços.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!formOpen) return;
    const cep = digits(form.zipCode);
    if (cep.length !== 8 || cep === lastCep.current) return;
    lastCep.current = cep;
    const controller = new AbortController();
    setCepLoading(true); setCepMessage(null);
    fetch(`https://viacep.com.br/ws/${cep}/json/`, { signal: controller.signal })
      .then((response) => { if (!response.ok) throw new Error(); return response.json(); })
      .then((data: { erro?: boolean; logradouro?: string; bairro?: string; localidade?: string; uf?: string }) => {
        if (data.erro) { setCepMessage('CEP não encontrado. Preencha o endereço manualmente.'); return; }
        setForm((current) => ({ ...current, street: data.logradouro ?? current.street, neighborhood: data.bairro ?? current.neighborhood, city: data.localidade ?? current.city, state: data.uf ?? current.state }));
        setCepMessage('Endereço localizado. Confira os dados.');
        requestAnimationFrame(() => numberRef.current?.focus());
      })
      .catch((reason) => { if (reason?.name !== 'AbortError') setCepMessage('Não foi possível consultar o CEP. Preencha o endereço manualmente.'); })
      .finally(() => setCepLoading(false));
    return () => controller.abort();
  }, [form.zipCode, formOpen]);

  function openCreate() { setEditing(null); setForm(empty); lastCep.current = ''; setFormError(null); setCepMessage(null); setFormOpen(true); }
  function openEdit(address: Address) {
    setEditing(address); lastCep.current = digits(address.zipCode);
    setForm({ label: address.label ?? '', recipientName: address.recipientName, zipCode: formatZip(address.zipCode), street: address.street, number: address.number, complement: address.complement ?? '', neighborhood: address.neighborhood, city: address.city, state: address.state, isPrimary: address.isPrimary });
    setFormError(null); setCepMessage(null); setFormOpen(true);
  }
  function close() { if (!saving) { setFormOpen(false); setEditing(null); } }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const zipCode = digits(form.zipCode);
    if (zipCode.length !== 8) return setFormError('CEP deve possuir 8 dígitos.');
    if (!form.recipientName.trim() || !form.street.trim() || !form.number.trim() || !form.neighborhood.trim() || !form.city.trim() || form.state.length !== 2) return setFormError('Preencha todos os campos obrigatórios.');
    const payload: AddressRequest = { label: form.label.trim(), recipientName: form.recipientName.trim(), zipCode, street: form.street.trim(), number: form.number.trim(), complement: form.complement.trim(), neighborhood: form.neighborhood.trim(), city: form.city.trim(), state: form.state.toUpperCase(), isPrimary: editing ? editing.isPrimary : form.isPrimary };
    setSaving(true); setFormError(null); setFeedback(null);
    try { const wasEditing = Boolean(editing); if (editing) await api.updateAddress(editing.id, payload); else await api.createAddress(payload); setFormOpen(false); setEditing(null); await load(); setFeedback(wasEditing ? 'Endereço atualizado com sucesso.' : 'Endereço adicionado com sucesso.'); }
    catch (reason) { setFormError(reason instanceof Error ? reason.message : 'Não foi possível salvar o endereço.'); }
    finally { setSaving(false); }
  }

  async function makePrimary(id: number) {
    setProcessing(id); setActionError(null); setFeedback(null);
    try {
      const updatedAddress = await api.setPrimaryAddress(id);
      setAddresses((current) => current
        .map((address) => address.id === updatedAddress.id
          ? updatedAddress
          : { ...address, isPrimary: false })
        .sort((first, second) => Number(second.isPrimary) - Number(first.isPrimary)));
      setFeedback('Endereço principal atualizado.');
    } catch {
      setActionError('Não foi possível definir o endereço principal.');
    } finally { setProcessing(null); }
  }
  async function remove(id: number) { setProcessing(id); setFeedback(null); try { await api.deleteAddress(id); setConfirmDelete(null); await load(); setFeedback('Endereço excluído com sucesso.'); } catch { setError('Não foi possível excluir o endereço.'); } finally { setProcessing(null); } }

  return <div className="addresses-panel">
    <div className="addresses-heading"><div><p className="eyebrow">ENDEREÇOS</p><h2>Seus endereços</h2></div>{addresses.length > 0 && <button type="button" onClick={openCreate}>Adicionar endereço</button>}</div>
    {feedback && <p className="panel-action-feedback" role="status">{feedback}</p>}
    {loading && <p className="account-status">Carregando seus endereços...</p>}
    {error && <div className="addresses-error" role="alert"><p>{error}</p><button type="button" onClick={() => void load()}>Tentar novamente</button></div>}
    {actionError && <p className="address-action-error" role="alert">{actionError}</p>}
    {!loading && !error && addresses.length === 0 && <div className="addresses-empty"><h3>Você ainda não possui endereços cadastrados.</h3><p>Adicione um endereço para agilizar suas próximas compras.</p><button type="button" onClick={openCreate}>Adicionar endereço</button></div>}
    {!loading && !error && addresses.length > 0 && <div className="addresses-grid">{addresses.map((address) => <article className={`address-card ${address.isPrimary ? 'primary' : ''}`} key={address.id}>
      <div className="address-card-heading">{address.label ? <span>{address.label}</span> : <span>ENDEREÇO</span>}{address.isPrimary && <strong>Endereço principal</strong>}</div>
      <h3>{address.recipientName}</h3><p>{address.street}, {address.number}</p>{address.complement && <p>{address.complement}</p>}<p>{address.neighborhood}</p><p>{address.city} - {address.state}</p><p className="address-zip">CEP {formatZip(address.zipCode)}</p>
      <div className="address-actions"><button type="button" onClick={() => openEdit(address)}>Editar</button><button type="button" onClick={() => setConfirmDelete(address.id)}>Excluir</button>{!address.isPrimary && <button className="primary-action" type="button" disabled={processing === address.id} onClick={() => void makePrimary(address.id)}>Tornar principal</button>}</div>
      {confirmDelete === address.id && <div className="address-delete-confirm"><p>Tem certeza que deseja excluir este endereço?</p><div><button type="button" onClick={() => setConfirmDelete(null)}>Cancelar</button><button type="button" disabled={processing === address.id} onClick={() => void remove(address.id)}>{processing === address.id ? 'Excluindo...' : 'Excluir endereço'}</button></div></div>}
    </article>)}</div>}
    {formOpen && <div className="vehicle-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}><section className="vehicle-modal address-modal" role="dialog" aria-modal="true" aria-labelledby="address-form-title"><div className="vehicle-modal-heading"><div><p className="eyebrow">ENDEREÇOS</p><h2 id="address-form-title">{editing ? 'Editar endereço' : 'Adicionar endereço'}</h2></div><button type="button" aria-label="Fechar" onClick={close}>×</button></div>
      <form className="vehicle-form address-form" onSubmit={submit}><div className="vehicle-form-grid">
        <Field label="Identificação do endereço" value={form.label} maxLength={50} onChange={(value) => setForm({ ...form, label: value })} />
        <Field label="Nome do destinatário *" value={form.recipientName} maxLength={150} required onChange={(value) => setForm({ ...form, recipientName: value })} />
        <Field label="CEP *" value={form.zipCode} inputMode="numeric" maxLength={9} required onChange={(value) => { lastCep.current = digits(value) === lastCep.current ? lastCep.current : ''; setForm({ ...form, zipCode: formatZip(value) }); }} />
        <Field label="Rua *" value={form.street} maxLength={200} required onChange={(value) => setForm({ ...form, street: value })} />
        <label><span>Número *</span><input ref={numberRef} value={form.number} maxLength={30} required onChange={(event) => setForm({ ...form, number: event.target.value })} /></label>
        <Field label="Complemento" value={form.complement} maxLength={150} onChange={(value) => setForm({ ...form, complement: value })} />
        <Field label="Bairro *" value={form.neighborhood} maxLength={120} required onChange={(value) => setForm({ ...form, neighborhood: value })} />
        <Field label="Cidade *" value={form.city} maxLength={120} required onChange={(value) => setForm({ ...form, city: value })} />
        <Field label="UF *" value={form.state} maxLength={2} required onChange={(value) => setForm({ ...form, state: value.toUpperCase().replace(/[^A-Z]/g, '') })} />
      </div>{!editing && <label className="vehicle-primary-check"><input type="checkbox" checked={form.isPrimary} onChange={(event) => setForm({ ...form, isPrimary: event.target.checked })} /><span>Usar como endereço principal</span></label>}
      {(cepLoading || cepMessage) && <p className="cep-feedback" role="status">{cepLoading ? 'Consultando CEP...' : cepMessage}</p>}{formError && <p className="vehicle-form-error" role="alert">{formError}</p>}<div className="vehicle-form-actions"><button type="button" onClick={close}>Cancelar</button><button type="submit" disabled={saving}>{saving ? 'Salvando...' : 'Salvar endereço'}</button></div></form>
    </section></div>}
  </div>;
}

function Field({ label, value, onChange, ...props }: { label: string; value: string; onChange: (value: string) => void } & Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>) {
  return <label><span>{label}</span><input {...props} value={value} onChange={(event) => onChange(event.target.value)} /></label>;
}
