import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = {
  title: 'Política de Privacidade | GARAGE',
  description: 'Entenda como a GARAGE trata dados pessoais durante o uso da loja, compras, pagamentos e entregas.',
};

export default function PrivacyPage() {
  return <InstitutionalPage eyebrow="LEGAL" title="Política de Privacidade" reviewNotice introduction="Este documento explica, de forma transparente, como os dados pessoais necessários ao funcionamento da GARAGE são tratados." sections={[
    { title: 'Dados tratados', content: <><p>Podemos tratar nome, e-mail, credenciais protegidas, foto de perfil quando fornecida pelo login Google, endereços de entrega, produtos favoritos, veículos cadastrados e informações relacionadas a pedidos.</p><p>Ao usar a busca, o catálogo ou o carrinho, dados técnicos indispensáveis à comunicação com a aplicação podem ser processados. O projeto não possui ferramenta de analytics, publicidade comportamental ou gerenciador próprio de cookies configurado.</p></> },
    { title: 'Conta e autenticação', content: <p>Os dados da conta são usados para autenticar o acesso, manter o perfil, verificar o e-mail, recuperar ou alterar a senha e proteger áreas restritas. Senhas locais são armazenadas como hash, nunca em texto legível. Contas Google dependem da identidade confirmada pelo provedor.</p> },
    { title: 'Pedidos e endereços', content: <p>Nome, e-mail, endereço escolhido, itens, quantidades, valores, modalidade de entrega e histórico de status são tratados para criar, processar e apresentar o pedido. O pedido conserva um retrato do endereço e da opção de entrega usados na compra.</p> },
    { title: 'Pagamentos', content: <p>Os pagamentos online são iniciados pelo backend da GARAGE e concluídos no ambiente hospedado do Mercado Pago. A GARAGE mantém somente os identificadores e estados necessários para conciliar o pedido; dados completos de cartão não são coletados nem armazenados pela loja.</p> },
    { title: 'Entrega e prestadores', content: <p>CEP, endereço, itens e opção de frete podem ser usados para cotação e execução da entrega. Dados podem ser compartilhados com prestadores apenas na medida necessária ao pagamento, comunicação transacional, hospedagem, segurança e logística. Os prestadores tratam dados conforme suas próprias obrigações legais e contratuais.</p> },
    { title: 'Finalidades e bases legais', content: <p>O tratamento pode ocorrer para executar contratos e procedimentos solicitados pelo cliente, cumprir obrigações legais ou regulatórias, prevenir fraude e exercer direitos, além de interesses legítimos avaliados de forma compatível com os direitos do titular.</p> },
    { title: 'Armazenamento e segurança', content: <p>Os dados são mantidos pelo período necessário às finalidades informadas, ao cumprimento de obrigações e ao exercício de direitos. São adotadas medidas técnicas e organizacionais compatíveis com o sistema, mas nenhum ambiente conectado é completamente imune a incidentes.</p> },
    { title: 'Direitos do titular', content: <p>Nos termos da LGPD, o titular pode solicitar, quando aplicável, confirmação do tratamento, acesso, correção, anonimização, bloqueio, eliminação, portabilidade, informação sobre compartilhamentos, revisão de decisões automatizadas e revogação do consentimento. Algumas informações podem precisar ser conservadas por obrigação legal ou para exercício de direitos.</p> },
    { title: 'Solicitações e contato', content: <p>Pedidos relacionados a dados pessoais deverão ser encaminhados pelo canal indicado na página de <Link href="/contato">Contato</Link>. Antes da publicação, a identificação do controlador e o canal definitivo precisam ser preenchidos: <strong>[IDENTIFICAÇÃO DO CONTROLADOR E CANAL DE PRIVACIDADE A DEFINIR ANTES DA PUBLICAÇÃO]</strong>.</p> },
    { title: 'Alterações desta política', content: <p>Esta política poderá ser atualizada para refletir mudanças legais ou no funcionamento da loja. A versão vigente será disponibilizada nesta página. O texto deve passar por revisão jurídica e empresarial antes da publicação em produção.</p> },
  ]} />;
}
