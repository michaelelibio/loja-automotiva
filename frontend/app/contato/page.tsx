import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';
import { BusinessContact, BusinessIdentity, BusinessReadinessNotice } from '@/components/BusinessInformation';
import { readBusinessProfile } from '@/lib/store/business-profile';

export const metadata: Metadata = { title: 'Contato | inGarage', description: 'Canais de atendimento e identificação da inGarage.' };

export default function ContactPage() {
  const profile = readBusinessProfile();
  return <InstitutionalPage eyebrow="INSTITUCIONAL" title="Contato" introduction="Atendimento para dúvidas sobre conta, pedidos, pagamentos, entregas, privacidade, trocas ou devoluções." sections={[
    { title: 'Identificação da loja', content: <><BusinessReadinessNotice profile={profile} /><BusinessIdentity profile={profile} /></> },
    { title: 'Canais de atendimento', content: <BusinessContact profile={profile} /> },
    { title: 'Privacidade', content: <><BusinessContact profile={profile} privacy /><p>Conheça o tratamento dos seus dados na <Link href="/privacidade">Política de Privacidade</Link>.</p></> },
    { title: 'Sobre seu pedido', content: <p>Ao entrar em contato, informe o número do pedido e o motivo da solicitação. Nunca envie senha, token de acesso ou dados completos de cartão. Consulte também <Link href="/entrega">Entrega</Link> e <Link href="/trocas-e-devolucoes">Trocas e devoluções</Link>.</p> },
  ]} />;
}
