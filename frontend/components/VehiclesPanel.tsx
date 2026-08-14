'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import * as vehiclesAPI from '@/lib/api/vehicles';
import type { Vehicle, VehicleRequest } from '@/lib/types/vehicle';
import { CarIcon } from '@/components/Icons';

type FormValues = { brand: string; model: string; year: string; version: string; licensePlate: string; isPrimary: boolean };
const emptyForm: FormValues = { brand: '', model: '', year: '', version: '', licensePlate: '', isPrimary: false };
const platePattern = /^[A-Z]{3}(?:[0-9]{4}|[0-9][A-Z][0-9]{2})$/;

export function VehiclesPanel({ onCountChange }: { onCountChange: (count: number) => void }) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Vehicle | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<FormValues>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

  const loadVehicles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await vehiclesAPI.getVehicles();
      setVehicles(data);
      onCountChange(data.length);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível carregar seus veículos.');
    } finally {
      setLoading(false);
    }
  }, [onCountChange]);

  useEffect(() => { void loadVehicles(); }, [loadVehicles]);

  function openCreate() {
    setEditing(null); setForm(emptyForm); setFormError(null); setFormOpen(true);
  }

  function openEdit(vehicle: Vehicle) {
    setEditing(vehicle);
    setForm({ brand: vehicle.brand, model: vehicle.model, year: String(vehicle.year), version: vehicle.version ?? '', licensePlate: vehicle.licensePlate ?? '', isPrimary: vehicle.isPrimary });
    setFormError(null); setFormOpen(true);
  }

  function closeForm() { if (!saving) { setFormOpen(false); setEditing(null); setFormError(null); } }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const brand = form.brand.trim();
    const model = form.model.trim();
    const year = Number(form.year);
    const licensePlate = form.licensePlate.trim().toUpperCase();
    const nextYear = new Date().getFullYear() + 1;
    if (!brand || !model) return setFormError('Preencha marca e modelo.');
    if (!Number.isInteger(year) || year < 1886 || year > nextYear) return setFormError(`Informe um ano entre 1886 e ${nextYear}.`);
    if (licensePlate && !platePattern.test(licensePlate)) return setFormError('Informe a placa no formato AAA1234 ou AAA1A23.');

    const payload: VehicleRequest = {
      brand, model, year, version: form.version.trim(), licensePlate,
      isPrimary: editing ? editing.isPrimary : form.isPrimary,
      ...(editing ? { imageUrl: editing.imageUrl } : {}),
    };
    setSaving(true); setFormError(null);
    try {
      if (editing) await vehiclesAPI.updateVehicle(editing.id, payload);
      else await vehiclesAPI.createVehicle(payload);
      setFormOpen(false); setEditing(null); setForm(emptyForm);
      await loadVehicles();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Não foi possível salvar o veículo.');
    } finally { setSaving(false); }
  }

  async function setPrimary(id: number) {
    setProcessingId(id); setError(null);
    try { await vehiclesAPI.setPrimaryVehicle(id); await loadVehicles(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Não foi possível definir o veículo principal.'); }
    finally { setProcessingId(null); }
  }

  async function remove(id: number) {
    setProcessingId(id); setError(null);
    try { await vehiclesAPI.deleteVehicle(id); setConfirmDeleteId(null); await loadVehicles(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Não foi possível excluir o veículo.'); }
    finally { setProcessingId(null); }
  }

  return <>
    <div className="vehicles-heading"><div><p className="eyebrow">VEÍCULOS</p><h2>Minha garagem</h2></div>{vehicles.length > 0 && <button type="button" onClick={openCreate}>Adicionar veículo</button>}</div>
    <div className="account-panel vehicles-panel">
      {loading && <p className="account-status">Carregando seus veículos...</p>}
      {error && <div className="vehicles-error" role="alert"><p>{error}</p><button type="button" onClick={() => void loadVehicles()}>Tentar novamente</button></div>}
      {!loading && !error && vehicles.length === 0 && <div className="vehicles-empty"><p className="eyebrow">SUA GARAGEM</p><h3>Nenhum veículo cadastrado.</h3><p>Cadastre seu carro para organizar sua garagem e facilitar futuras recomendações.</p><button type="button" onClick={openCreate}>Adicionar veículo</button></div>}
      {!loading && vehicles.length > 0 && <div className="vehicles-grid">{vehicles.map((vehicle) => <article key={vehicle.id} className={`vehicle-card ${vehicle.isPrimary ? 'primary' : ''}`}>
        <VehicleImage vehicle={vehicle} />
        <div className="vehicle-card-content"><div className="vehicle-card-top"><span>{vehicle.brand}</span>{vehicle.isPrimary && <strong>Veículo principal</strong>}</div>
        <h3>{vehicle.model}</h3>{vehicle.version && <p>{vehicle.version}</p>}
        <div className="vehicle-meta"><span>{vehicle.year}</span>{vehicle.licensePlate && <><i>•</i><span>{vehicle.licensePlate}</span></>}</div></div>
        <div className="vehicle-actions"><button type="button" onClick={() => openEdit(vehicle)}>Editar</button><button type="button" onClick={() => setConfirmDeleteId(vehicle.id)}>Excluir</button>{!vehicle.isPrimary && <button type="button" className="primary-action" disabled={processingId === vehicle.id} onClick={() => void setPrimary(vehicle.id)}>Tornar principal</button>}</div>
        {confirmDeleteId === vehicle.id && <div className="vehicle-delete-confirm"><p>Excluir {vehicle.brand} {vehicle.model}?</p><div><button type="button" onClick={() => setConfirmDeleteId(null)}>Cancelar</button><button type="button" disabled={processingId === vehicle.id} onClick={() => void remove(vehicle.id)}>{processingId === vehicle.id ? 'Excluindo...' : 'Excluir'}</button></div></div>}
      </article>)}</div>}
    </div>
    {formOpen && <div className="vehicle-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeForm(); }}><section className="vehicle-modal" role="dialog" aria-modal="true" aria-labelledby="vehicle-form-title"><div className="vehicle-modal-heading"><div><p className="eyebrow">MINHA GARAGEM</p><h2 id="vehicle-form-title">{editing ? 'Editar veículo' : 'Adicionar veículo'}</h2></div><button type="button" aria-label="Fechar" onClick={closeForm}>×</button></div>
      <form className="vehicle-form" onSubmit={submit}><div className="vehicle-form-grid">
        <label><span>Marca *</span><input value={form.brand} maxLength={80} onChange={(e) => setForm({ ...form, brand: e.target.value })} required /></label>
        <label><span>Modelo *</span><input value={form.model} maxLength={120} onChange={(e) => setForm({ ...form, model: e.target.value })} required /></label>
        <label><span>Ano *</span><input type="number" min={1886} max={new Date().getFullYear() + 1} value={form.year} onChange={(e) => setForm({ ...form, year: e.target.value })} required /></label>
        <label><span>Versão</span><input value={form.version} maxLength={150} onChange={(e) => setForm({ ...form, version: e.target.value })} /></label>
        <label><span>Placa</span><input value={form.licensePlate} maxLength={7} placeholder="ABC1D23" onChange={(e) => setForm({ ...form, licensePlate: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '') })} /></label>
      </div>{!editing && <label className="vehicle-primary-check"><input type="checkbox" checked={form.isPrimary} onChange={(e) => setForm({ ...form, isPrimary: e.target.checked })} /><span>Definir como veículo principal</span></label>}
      {formError && <p className="vehicle-form-error" role="alert">{formError}</p>}<div className="vehicle-form-actions"><button type="button" onClick={closeForm}>Cancelar</button><button type="submit" disabled={saving}>{saving ? 'Salvando...' : 'Salvar veículo'}</button></div></form>
    </section></div>}
  </>;
}

function VehicleImage({ vehicle }: { vehicle: Vehicle }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const [showNotice, setShowNotice] = useState(false);
  const showImage = Boolean(vehicle.imageUrl) && failedUrl !== vehicle.imageUrl;

  return <div className="vehicle-visual">
    <div className="vehicle-image-frame">
      {showImage ? (
        <img src={vehicle.imageUrl!} alt={`${vehicle.brand} ${vehicle.model}`} onError={() => setFailedUrl(vehicle.imageUrl)} />
      ) : (
        <div className="vehicle-image-placeholder" aria-label={`Sem foto de ${vehicle.brand} ${vehicle.model}`} role="img">
          <span className="vehicle-placeholder-mark">G</span><CarIcon /><small>GARAGE</small>
        </div>
      )}
    </div>
    <button type="button" className="vehicle-photo-action" onClick={() => setShowNotice(true)}>{vehicle.imageUrl ? 'Alterar foto' : 'Adicionar foto'}</button>
    {showNotice && <p className="vehicle-photo-notice" role="status">Upload de fotos em breve.</p>}
  </div>;
}
