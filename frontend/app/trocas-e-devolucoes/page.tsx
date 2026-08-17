import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = { title: 'Trocas e Devoluções | GARAGE', description: 'Orientações da GARAGE sobre arrependimento, devoluções e produtos com problemas.' };

export default function ReturnsPage() {
  return <InstitutionalPage eyebrow="AJUDA" title="Trocas e devoluções" reviewNotice introduction="As solicitações são tratadas conforme o Código de Defesa do Consumidor. Esta página não reduz garantias ou outros direitos previstos em lei." sections={[
    { title: 'Direito de arrependimento', content: <p>Em compras realizadas online, o consumidor pode exercer o direito de arrependimento no prazo legal de 7 dias corridos, contado do recebimento do produto, nos termos do artigo 49 do Código de Defesa do Consumidor.</p> },
    { title: 'Como solicitar', content: <p>Entre em contato dentro do prazo aplicável, informe o número do pedido e descreva a solicitação. O canal definitivo está pendente e será publicado em <Link href="/contato">Contato</Link>: <strong>[CANAL DE TROCAS E DEVOLUÇÕES A DEFINIR ANTES DA PUBLICAÇÃO]</strong>.</p> },
    { title: 'Conservação e envio', content: <p>Enquanto aguarda as orientações, conserve o produto e seus acessórios, documentos e embalagem, quando disponíveis. O exercício do direito de arrependimento não pode ser condicionado por exigências abusivas. O endereço de devolução e o procedimento de logística reversa serão informados no atendimento; eles ainda precisam de definição operacional antes da publicação.</p> },
    { title: 'Defeito, item incorreto ou avaria', content: <p>Comunique defeitos, divergências ou avarias assim que identificados e forneça informações que permitam analisar o caso. A avaliação técnica poderá ocorrer quando juridicamente aplicável, sempre preservando os prazos e soluções previstos no CDC.</p> },
    { title: 'Reembolso', content: <p>Quando devido, o reembolso será encaminhado de acordo com o meio de pagamento utilizado no Mercado Pago e as regras aplicáveis. O prazo operacional de devolução de valores ainda não está definido e deve ser informado ao cliente durante o atendimento, sem prejuízo dos prazos legais.</p> },
    { title: 'Pendências operacionais', content: <p><strong>[DEFINIR ANTES DA PUBLICAÇÃO: canal responsável, endereço de retorno, procedimento de logística reversa e prazos operacionais de análise e reembolso]</strong>. Este conteúdo deve receber revisão jurídica e operacional antes do uso em produção.</p> },
  ]} />;
}
