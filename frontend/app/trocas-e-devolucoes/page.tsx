import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';
import { BusinessContact, BusinessReadinessNotice, PolicyVersion } from '@/components/BusinessInformation';
import { readBusinessProfile } from '@/lib/store/business-profile';

export const metadata: Metadata = { title: 'Trocas e Devoluções | inGarage', description: 'Orientações sobre arrependimento, devoluções e produtos com problemas.' };

export default function ReturnsPage() {
  const profile = readBusinessProfile();
  return <InstitutionalPage eyebrow="AJUDA" title="Trocas e devoluções" introduction="As solicitações são tratadas conforme o Código de Defesa do Consumidor, sem redução de garantias ou de outros direitos previstos em lei." sections={[
    { title: 'Direito de arrependimento', content: <><BusinessReadinessNotice profile={profile} /><PolicyVersion profile={profile} /><p>Nas compras fora do estabelecimento comercial, inclusive pela internet, o consumidor pode desistir no prazo legal de 7 dias a contar da assinatura ou do recebimento do produto ou serviço, conforme o artigo 49 do Código de Defesa do Consumidor.</p></> },
    { title: 'Como solicitar', content: <><BusinessContact profile={profile} /><p>Informe o número do pedido e a solicitação. Nunca envie senha ou dados completos de cartão.</p>{profile.returnInstructions ? <p>{profile.returnInstructions}</p> : <p>O procedimento operacional de devolução e logística reversa ainda não foi publicado. Não envie o produto a um endereço do fornecedor sem orientação confirmada da inGarage.</p>}</> },
    { title: 'Produto e acessórios', content: <p>Enquanto aguarda orientações, conserve o produto e seus acessórios, documentos e embalagem, quando disponíveis. O direito de arrependimento não depende de exigências abusivas e inclui a devolução dos valores pagos conforme a legislação.</p> },
    { title: 'Defeito, item incorreto ou avaria', content: <p>Comunique o problema pelo <Link href="/contato">atendimento</Link> e forneça informações que permitam analisar o caso. Eventuais avaliações e soluções devem preservar os prazos e direitos previstos no Código de Defesa do Consumidor.</p> },
    { title: 'Reembolso', content: profile.refundTerms ? <p>{profile.refundTerms}</p> : <p>Os procedimentos e prazos operacionais de reembolso ainda não foram publicados. Essa pendência não afasta a devolução de valores quando devida nem os prazos legais.</p> },
  ]} />;
}
