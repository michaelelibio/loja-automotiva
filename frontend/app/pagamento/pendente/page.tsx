import { PaymentReturnPage } from '@/components/PaymentReturnPage';

export default function PaymentPendingPage() {
  return <PaymentReturnPage eyebrow="PAGAMENTO PENDENTE" title="Pagamento em processamento"
    description="O Mercado Pago ainda está processando o pagamento. Acompanhe o estado atualizado nos detalhes do pedido." />;
}
