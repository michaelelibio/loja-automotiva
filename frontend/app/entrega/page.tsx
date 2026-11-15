import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';
import { BusinessReadinessNotice, PolicyVersion } from '@/components/BusinessInformation';
import { readBusinessProfile } from '@/lib/store/business-profile';

export const metadata: Metadata = { title: 'Entrega | inGarage', description: 'Condições de entrega dos pedidos inGarage.' };

export default function DeliveryPage() {
  const profile = readBusinessProfile();
  return <InstitutionalPage eyebrow="AJUDA" title="Entrega" introduction="Confira os produtos, o total e o endereço antes de finalizar sua compra." sections={[
    { title: 'Condições de entrega', content: <><BusinessReadinessNotice profile={profile} /><PolicyVersion profile={profile} />{profile.deliveryTerms ? <p>{profile.deliveryTerms}</p> : <p>O prazo, a forma de contagem e as demais condições operacionais de entrega ainda não foram publicados. Não há um prazo de entrega confirmado nesta página.</p>}</> },
    { title: 'Entrega grátis e total do pedido', content: <p>A entrega dos produtos habilitados no catálogo custa R$ 0,00. Confira os itens, as quantidades e o total apresentado no checkout. Os valores e o endereço utilizados na compra ficam registrados no pedido.</p> },
    { title: 'Endereço e destinatário', content: <p>Confira nome completo, telefone, dados fiscais, data de nascimento quando solicitada, rua, número, complemento, bairro, cidade, estado e CEP. Os produtos enviados por fornecedores podem exigir dados do destinatário para processar a entrega. Se identificar um erro depois da compra, fale pelo canal de <Link href="/contato">Contato</Link>.</p> },
    { title: 'Acompanhamento e atrasos', content: <p>Consulte o histórico na área de pedidos. O status de pagamento da compra não significa que o produto foi enviado. Em caso de atraso ou dúvida sobre a entrega, informe o número do pedido no <Link href="/contato">atendimento</Link>.</p> },
  ]} />;
}
