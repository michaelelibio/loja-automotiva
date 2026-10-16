'use client';

import { maskBirthDate, maskCpf, maskPhone, type AliExpressRecipientInput, type RecipientErrors } from '@/lib/checkout/recipient';

export function AliExpressRecipientFields({ value, onChange, errors, disabled }: {
  value: AliExpressRecipientInput; onChange: (value: AliExpressRecipientInput) => void; errors: RecipientErrors; disabled: boolean;
}) {
  return <fieldset className="checkout-fieldset checkout-recipient" disabled={disabled} aria-describedby="recipient-purpose">
    <legend>Dados do destinatário para entrega ao Brasil</legend>
    <p id="recipient-purpose">Este pedido contém produtos AliExpress. Informe os dados da pessoa indicada no endereço de entrega. Eles serão usados para preparar a entrega e não serão exibidos no pedido.</p>
    <div className="checkout-recipient-grid">
      <label htmlFor="recipient-cpf">CPF<input id="recipient-cpf" name="recipientCpf" inputMode="numeric" autoComplete="off" value={value.cpf} onChange={(event) => onChange({ ...value, cpf: maskCpf(event.target.value) })} placeholder="000.000.000-00" maxLength={14} required aria-invalid={!!errors.cpf} aria-describedby={errors.cpf ? 'recipient-cpf-error' : undefined} />{errors.cpf && <small id="recipient-cpf-error">{errors.cpf}</small>}</label>
      <label htmlFor="recipient-birth-date">Data de nascimento<input id="recipient-birth-date" name="recipientBirthDate" inputMode="numeric" autoComplete="off" value={value.birthDate} onChange={(event) => onChange({ ...value, birthDate: maskBirthDate(event.target.value) })} placeholder="DD/MM/AAAA" maxLength={10} required aria-invalid={!!errors.birthDate} aria-describedby={errors.birthDate ? 'recipient-birth-date-error' : undefined} />{errors.birthDate && <small id="recipient-birth-date-error">{errors.birthDate}</small>}</label>
      <label htmlFor="recipient-phone">Telefone com DDD<input id="recipient-phone" name="recipientPhone" type="tel" autoComplete="off" value={value.phone} onChange={(event) => onChange({ ...value, phone: maskPhone(event.target.value) })} placeholder="(00) 00000-0000" maxLength={15} required aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'recipient-phone-error' : undefined} />{errors.phone && <small id="recipient-phone-error">{errors.phone}</small>}</label>
    </div>
    {errors.address && <p className="checkout-error" role="alert">{errors.address}</p>}
  </fieldset>;
}
