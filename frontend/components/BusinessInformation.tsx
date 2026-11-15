import type { BusinessProfile } from '@/lib/store/business-profile';

export function BusinessReadinessNotice({ profile }: { profile: BusinessProfile }) {
  if (profile.ready) return null;
  return <aside className="institutional-review" aria-label="Informações em revisão">
    <strong>Informações comerciais em revisão</strong>
    <p>A identificação, os canais de atendimento e os procedimentos desta página ainda estão em revisão. As informações não publicadas não representam condições confirmadas de venda.</p>
  </aside>;
}

export function BusinessIdentity({ profile }: { profile: BusinessProfile }) {
  return <>
    <p><strong>Marca:</strong> inGarage.</p>
    {profile.legalName ? <p><strong>Responsável pela loja e pelo tratamento dos dados:</strong> {profile.legalName}.</p> : <p>A identificação do responsável pela loja ainda não foi publicada.</p>}
    {profile.taxId && profile.taxIdType && <p><strong>{profile.taxIdType}:</strong> {profile.taxId}.</p>}
    {profile.address && <p><strong>Endereço comercial:</strong> {profile.address}.</p>}
  </>;
}

export function BusinessContact({ profile, privacy = false }: { profile: BusinessProfile; privacy?: boolean }) {
  const address = privacy ? profile.privacyEmail : profile.supportEmail;
  return <>
    {address ? <p><strong>{privacy ? 'Privacidade e dados pessoais' : 'Atendimento'}:</strong> <a href={`mailto:${address}`}>{address}</a>.</p> : <p>O canal de {privacy ? 'privacidade' : 'atendimento'} ainda não foi publicado.</p>}
    {!privacy && profile.phone && <p><strong>Telefone:</strong> <a href={`tel:${profile.phone}`}>{profile.phone}</a>.</p>}
    {profile.supportHours && <p><strong>Atendimento:</strong> {profile.supportHours}.</p>}
  </>;
}

export function PolicyVersion({ profile }: { profile: BusinessProfile }) {
  if (!profile.policyUpdatedAt) return null;
  const [year, month, day] = profile.policyUpdatedAt.split('-');
  return <p>Atualizado em <time dateTime={profile.policyUpdatedAt}>{day}/{month}/{year}</time>.</p>;
}
