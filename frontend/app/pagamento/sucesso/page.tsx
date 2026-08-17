import { PaymentReturnPage } from '@/components/PaymentReturnPage';

export default function PaymentSuccessPage() {
  return <PaymentReturnPage eyebrow="PAGAMENTO RECEBIDO" title="Estamos confirmando seu pedido"
    description="O Mercado Pago recebeu o pagamento. A confirmação final aparecerá nos detalhes do pedido após a validação segura." />;
}
