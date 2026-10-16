import type { AdminOrder } from '@/lib/types/admin-order';

const labels: Record<string, string> = {
  'recipient.cpf': 'CPF', 'recipient.birthDate': 'Data de nascimento', 'recipient.phone': 'Telefone com DDD',
  'shippingAddress.recipientName': 'Nome completo do destinatário', 'shippingAddress.zipCode': 'CEP',
  'shippingAddress.street': 'Rua', 'shippingAddress.number': 'Número', 'shippingAddress.neighborhood': 'Bairro',
  'shippingAddress.city': 'Cidade', 'shippingAddress.state': 'UF',
};
export function AliExpressRecipientStatus({ status }: { status: AdminOrder['recipientData'] }) {
  if (!status?.required) return null;
  return <section className="admin-card"><h2>Dados do destinatário AliExpress</h2>
    {status.complete ? <p role="status">Dados completos: pronto para validação de frete.</p> : <><p role="status">Dados incompletos. Campos pendentes ou inválidos:</p><ul>{status.pendingFields.map((field) => <li key={field}>{labels[field] || 'Dados do destinatário'}</li>)}</ul></>}
    <p className="admin-muted">A conferência dos dados não confirma o frete nem libera a criação de ordens no AliExpress.</p>
  </section>;
}
