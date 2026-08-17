import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = { title: 'Termos de Uso | GARAGE', description: 'Condições para utilização da plataforma e realização de compras na GARAGE.' };

export default function TermsPage() {
  return <InstitutionalPage eyebrow="LEGAL" title="Termos de Uso" reviewNotice introduction="Estes termos apresentam as condições gerais de uso da GARAGE sem limitar direitos assegurados pela legislação brasileira e pelo Código de Defesa do Consumidor." sections={[
    { title: 'Uso da plataforma', content: <p>O cliente deve utilizar a loja de forma lícita, respeitando estes termos, terceiros e a segurança da plataforma. Tentativas de fraude, acesso indevido, interferência técnica ou uso de conteúdo sem autorização não são permitidas.</p> },
    { title: 'Cadastro e conta', content: <p>O cliente é responsável por fornecer dados verdadeiros e atualizados e por proteger suas credenciais. A conta pode usar e-mail e senha ou autenticação Google. O login é permitido mesmo enquanto a verificação do e-mail estiver pendente.</p> },
    { title: 'Produtos, preços e estoque', content: <p>Características, imagens, preços e disponibilidade são apresentados nas páginas dos produtos. A inclusão no carrinho não reserva estoque. Valores e disponibilidade aplicáveis são confirmados pelo backend na criação do pedido, e erros evidentes serão tratados conforme a legislação.</p> },
    { title: 'Pedidos e pagamento', content: <p>O pedido depende da validação dos itens, estoque, endereço e modalidade de entrega. Os pagamentos online são concluídos no ambiente seguro do Mercado Pago, com os meios disponíveis para a conta. Um pedido pode permanecer aguardando pagamento e expirar conforme as regras exibidas no próprio pedido.</p> },
    { title: 'Entrega', content: <p>O valor e a estimativa disponíveis são calculados a partir do CEP, endereço e itens, sendo confirmados ao criar o pedido. Consulte os detalhes na página de <Link href="/entrega">Entrega</Link>.</p> },
    { title: 'Cancelamentos, trocas e devoluções', content: <p>Solicitações serão analisadas conforme a legislação aplicável e a política específica, sem exclusão dos direitos do consumidor. Consulte <Link href="/trocas-e-devolucoes">Trocas e devoluções</Link>.</p> },
    { title: 'Propriedade intelectual', content: <p>A marca, identidade visual, textos, interfaces e demais conteúdos da plataforma são protegidos pela legislação aplicável. O acesso à loja não transfere direitos de propriedade intelectual ao usuário.</p> },
    { title: 'Disponibilidade técnica', content: <p>A plataforma pode ficar temporariamente indisponível para manutenção, atualização ou por eventos fora do controle razoável da operação. Isso não afasta responsabilidades previstas em lei nem direitos associados a pedidos já realizados.</p> },
    { title: 'Alterações, legislação e contato', content: <p>Os termos podem ser atualizados quando o serviço ou a legislação mudar, com publicação da versão vigente nesta rota. Aplica-se a legislação brasileira, preservados os direitos do consumidor. Dúvidas devem ser encaminhadas pela página de <Link href="/contato">Contato</Link>. <strong>[IDENTIFICAÇÃO EMPRESARIAL A DEFINIR ANTES DA PUBLICAÇÃO]</strong>.</p> },
  ]} />;
}
