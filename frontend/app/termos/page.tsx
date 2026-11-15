import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';
import { BusinessIdentity, BusinessReadinessNotice, PolicyVersion } from '@/components/BusinessInformation';
import { readBusinessProfile } from '@/lib/store/business-profile';

export const metadata: Metadata = { title: 'Termos de Uso | inGarage', description: 'Condições de uso da plataforma e de compras na inGarage.' };

export default function TermsPage() {
  const profile = readBusinessProfile();
  return <InstitutionalPage eyebrow="LEGAL" title="Termos de Uso" introduction="Condições gerais de uso da inGarage, preservados os direitos assegurados pela legislação brasileira e pelo Código de Defesa do Consumidor." sections={[
    { title: 'Responsável pela loja', content: <><BusinessReadinessNotice profile={profile} /><PolicyVersion profile={profile} /><BusinessIdentity profile={profile} /></> },
    { title: 'Uso da plataforma e conta', content: <p>Utilize a loja de forma lícita e forneça dados verdadeiros e atualizados. Proteja suas credenciais. Tentativas de fraude, acesso indevido ou interferência na segurança da plataforma não são permitidas.</p> },
    { title: 'Produtos, preços e estoque', content: <p>Confira descrição, variante, preço, quantidade e disponibilidade de cada produto antes de comprar. Incluir um item no carrinho não reserva estoque. A disponibilidade e os valores são validados na criação do pedido. Eventuais divergências serão tratadas com o consumidor conforme a legislação.</p> },
    { title: 'Pedidos e pagamento', content: <p>O pedido depende da validação dos itens, estoque, destinatário e endereço. O pagamento online é concluído no ambiente do Mercado Pago, com os meios disponibilizados por ele. A confirmação depende da aprovação do pagamento. Acompanhe o pedido na sua conta.</p> },
    { title: 'Entrega', content: <p>Os produtos habilitados no catálogo possuem entrega grátis. Confira as demais condições em <Link href="/entrega">Entrega</Link>. O pagamento aprovado não equivale à confirmação de envio.</p> },
    { title: 'Cancelamentos, trocas e devoluções', content: <p>Consulte <Link href="/trocas-e-devolucoes">Trocas e devoluções</Link> para orientações sobre arrependimento, produto com problema e reembolso. Nenhuma condição desta página limita os direitos previstos em lei.</p> },
    { title: 'Dados pessoais', content: <p>O tratamento de dados para conta, compra, pagamento e entrega está descrito na <Link href="/privacidade">Política de Privacidade</Link>.</p> },
    { title: 'Atualizações e atendimento', content: <p>As condições podem ser atualizadas para refletir mudanças no serviço e na legislação, preservados os direitos relativos às compras realizadas. Dúvidas e solicitações devem ser encaminhadas pelos canais de <Link href="/contato">Contato</Link>.</p> },
  ]} />;
}
