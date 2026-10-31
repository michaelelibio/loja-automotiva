import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = { title: 'Entrega | inGarage', description: 'Confira a disponibilidade de entrega grátis para seu pedido.' };

export default function DeliveryPage() {
  return <InstitutionalPage eyebrow="AJUDA" title="Entrega" introduction="Os produtos habilitados pela nossa equipe são vendidos com entrega grátis. Confira os itens e o endereço antes de finalizar sua compra." sections={[
    { title: 'Entrega grátis', content: <p>A entrega dos produtos habilitados no catálogo custa R$ 0,00. O checkout apresenta o total dos produtos, sem cobrança de frete.</p> },
    { title: 'Total do pedido', content: <p>Confira os produtos, as quantidades e o total antes de finalizar. O valor acordado e os dados de entrega ficam registrados no pedido.</p> },
    { title: 'Endereço de entrega', content: <p>Confira destinatário, telefone, dados fiscais, logradouro, número, complemento, bairro, cidade, estado e CEP antes de concluir. Se houver erro, use o canal de <Link href="/contato">Contato</Link>.</p> },
    { title: 'Contagem do prazo', content: <p>O sistema registra uma estimativa em dias, mas ainda não define publicamente se a contagem começa no pagamento, na preparação ou no envio, nem se considera dias úteis ou corridos. <strong>[CRITÉRIO DE INÍCIO E CONTAGEM DO PRAZO A DEFINIR ANTES DA PUBLICAÇÃO]</strong>.</p> },
    { title: 'Acompanhamento e atrasos', content: <p>O cliente pode acompanhar na área de pedidos os status aguardando pagamento, pago, em preparação, enviado e entregue. A plataforma ainda não oferece código ou página de rastreamento. Em caso de atraso, consulte o pedido e entre em contato para análise.</p> },
    { title: 'Seleção de produtos', content: <p>Nossa equipe seleciona e habilita os produtos disponíveis para compra com entrega grátis.</p> },
  ]} />;
}
