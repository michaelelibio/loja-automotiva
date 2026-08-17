import type { Metadata } from 'next';
import Link from 'next/link';
import { InstitutionalPage } from '@/components/InstitutionalPage';

export const metadata: Metadata = { title: 'Contato | GARAGE', description: 'Canais de atendimento e orientações para falar com a GARAGE.' };

export default function ContactPage() {
  return <InstitutionalPage eyebrow="INSTITUCIONAL" title="Contato" reviewNotice introduction="Use o atendimento para dúvidas sobre conta, pedidos, pagamentos, entregas, privacidade, trocas ou devoluções." sections={[
    { title: 'Canal de atendimento', content: <><p>O projeto ainda não possui um e-mail, telefone, WhatsApp, endereço comercial ou horário de atendimento empresarial comprovadamente configurado.</p><p><strong>[CANAL DE ATENDIMENTO A DEFINIR ANTES DA PUBLICAÇÃO]</strong></p></> },
    { title: 'Sobre formulários', content: <p>Não há endpoint de contato no backend. Por isso, esta página não apresenta um formulário que simule o envio de mensagens. Quando um canal real for definido, ele deverá substituir explicitamente a pendência acima.</p> },
    { title: 'Antes de entrar em contato', content: <p>Para pedidos, tenha o número disponível e nunca envie sua senha, token de acesso ou dados de pagamento. Informações sobre frete estão em <Link href="/entrega">Entrega</Link>, e orientações de devolução em <Link href="/trocas-e-devolucoes">Trocas e devoluções</Link>.</p> },
    { title: 'Identificação empresarial', content: <p><strong>[RAZÃO SOCIAL, CNPJ E ENDEREÇO EMPRESARIAL A DEFINIR ANTES DA PUBLICAÇÃO]</strong>. Nenhum desses dados foi encontrado no projeto e eles não devem ser substituídos por informações fictícias.</p> },
  ]} />;
}
