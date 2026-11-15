import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';
import { BusinessContact, BusinessIdentity, BusinessReadinessNotice, PolicyVersion } from '@/components/BusinessInformation';
import { readBusinessProfile } from '@/lib/store/business-profile';

export const metadata: Metadata = { title: 'Política de Privacidade | inGarage', description: 'Tratamento de dados pessoais na conta, nas compras, nos pagamentos e nas entregas.' };

export default function PrivacyPage() {
  const profile = readBusinessProfile();
  return <InstitutionalPage eyebrow="LEGAL" title="Política de Privacidade" introduction="Conheça os dados usados no funcionamento da loja e os canais para exercer seus direitos." sections={[
    { title: 'Responsável e contato', content: <><BusinessReadinessNotice profile={profile} /><PolicyVersion profile={profile} /><BusinessIdentity profile={profile} /><BusinessContact profile={profile} privacy /></> },
    { title: 'Conta e funcionalidades', content: <p>Tratamos nome, e-mail, credenciais protegidas e, no login Google, a identidade e a foto fornecidas pelo provedor. Também são armazenados os endereços, favoritos e veículos que você cadastrar. Esses dados permitem autenticar o acesso, manter a conta, verificar o e-mail e recuperar ou alterar a senha. Senhas locais são armazenadas como hash.</p> },
    { title: 'Pedidos e destinatário', content: <p>Para processar a compra, tratamos os itens, as quantidades, os valores, o histórico do pedido e os dados de entrega. Quando exigidos para produtos AliExpress, tratamos também CPF, data de nascimento e telefone do destinatário. O pedido conserva uma cópia dos dados usados na compra; uma correção administrativa nessa cópia não altera automaticamente o cadastro geral.</p> },
    { title: 'Pagamento e entrega', content: <p>Os pagamentos são concluídos no ambiente do Mercado Pago. A loja mantém os identificadores e estados necessários à conciliação, sem coletar dados completos de cartão. Dados do destinatário, incluindo CPF e nascimento quando exigidos, são enviados ao AliExpress para criar e processar o pedido do fornecedor e sua entrega. O envio ao fornecedor ocorre após a confirmação do pagamento da compra.</p> },
    { title: 'Prestadores e compartilhamento', content: <><p>Usamos Mercado Pago para pagamento, AliExpress e seus participantes logísticos para atendimento dos produtos vinculados, Resend para mensagens transacionais e Railway e Vercel para hospedagem. O login Google é opcional. Compartilhamos os dados necessários à função de cada prestador; o AliExpress pode envolver tratamento fora do Brasil.</p>{profile.privacyTransfers ? <p>{profile.privacyTransfers}</p> : <p>As condições documentadas de compartilhamento e transferência internacional ainda estão em revisão e não foram publicadas integralmente.</p>}</> },
    { title: 'Finalidades e conservação', content: <><p>Os dados necessários à compra são usados para executar o pedido e a entrega; também podem ser conservados para cumprir obrigações legais e exercer direitos. As demais finalidades e condições devem ser avaliadas conforme a Lei Geral de Proteção de Dados.</p>{profile.privacyRetention ? <p>{profile.privacyRetention}</p> : <p>Os critérios de conservação por categoria de dados ainda não foram publicados.</p>}</> },
    { title: 'Armazenamento no navegador', content: <p>A loja usa armazenamento do navegador para manter a sessão autenticada e o carrinho. Ao sair da conta, o acesso local à sessão é removido. Não compartilhe o dispositivo com a conta aberta.</p> },
    { title: 'Direitos e solicitações', content: <p>Você pode solicitar, quando aplicável, confirmação de tratamento, acesso, correção e as demais providências previstas na LGPD, inclusive informações sobre compartilhamentos, anonimização, bloqueio ou eliminação, observadas as hipóteses legais de conservação. Use o canal de privacidade informado em <Link href="/contato">Contato</Link>; poderão ser solicitadas informações proporcionais para verificar sua identidade.</p> },
    { title: 'Atualizações', content: <p>Alterações nesta política serão publicadas nesta página, com a data da versão confirmada. As solicitações relativas a pedidos e dados pessoais continuam sujeitas aos direitos previstos em lei.</p> },
  ]} />;
}
