import { PaymentReturnPage } from '@/components/PaymentReturnPage';

export default function PaymentFailurePage() {
  return <PaymentReturnPage eyebrow="PAGAMENTO NÃO CONCLUÍDO" title="Não foi possível concluir o pagamento"
    description="Volte ao pedido para tentar novamente. Nenhuma confirmação é feita por esta página de retorno." />;
}
