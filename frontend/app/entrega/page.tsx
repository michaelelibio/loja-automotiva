import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = { title: 'Entrega | GARAGE', description: 'Saiba como o frete é calculado e apresentado nos pedidos da GARAGE.' };

export default function DeliveryPage() {
  return <InstitutionalPage eyebrow="AJUDA" title="Entrega" introduction="O frete é cotado durante o checkout a partir dos dados do pedido. Valores e estimativas desta página não são fixos: prevalecem os dados confirmados pelo backend em cada compra." sections={[
    { title: 'Cálculo do frete', content: <p>Depois que um endereço válido é selecionado e há itens no carrinho, a GARAGE envia ao backend o CEP, os identificadores dos produtos e as quantidades. As opções disponíveis retornam com nome, preço e estimativa em dias. O cliente escolhe uma opção antes de finalizar.</p> },
    { title: 'Valor e prazo estimado', content: <p>O resumo do checkout apresenta subtotal, frete selecionado e total estimado. Ao criar o pedido, o frontend envia apenas o código da modalidade; o backend recalcula e registra os valores oficiais, além de guardar um retrato do nome, preço e prazo estimado da entrega.</p> },
    { title: 'Endereço de entrega', content: <p>A cotação utiliza o CEP do endereço selecionado. Confira destinatário, logradouro, número, complemento, bairro, cidade, estado e CEP antes de concluir. Não há atualmente uma regra publicada para correção de endereço após a criação do pedido; se houver erro, use o canal de <Link href="/contato">Contato</Link>.</p> },
    { title: 'Contagem do prazo', content: <p>O sistema registra uma estimativa em dias, mas ainda não define publicamente se a contagem começa no pagamento, na preparação ou no envio, nem se considera dias úteis ou corridos. <strong>[CRITÉRIO DE INÍCIO E CONTAGEM DO PRAZO A DEFINIR ANTES DA PUBLICAÇÃO]</strong>.</p> },
    { title: 'Acompanhamento e atrasos', content: <p>O cliente pode acompanhar na área de pedidos os status aguardando pagamento, pago, em preparação, enviado e entregue. A plataforma ainda não oferece código ou página de rastreamento. Em caso de atraso, consulte o pedido e entre em contato para análise.</p> },
    { title: 'Cobertura e condições', content: <p>A disponibilidade depende das opções retornadas para o endereço no checkout. O projeto não define cobertura nacional, transportadora específica, número de tentativas de entrega ou procedimento para destinatário ausente. Essas condições deverão ser informadas quando houver definição operacional.</p> },
  ]} />;
}
