import Link from 'next/link';

export default function AdminPage() {
  return <><header className="admin-page-heading"><p className="eyebrow">PAINEL ADMINISTRATIVO</p><h1>Operação GARAGE</h1><p>Acompanhe e avance os pedidos da loja.</p></header><section className="admin-dashboard-card"><div><span>01</span><p className="eyebrow">PEDIDOS</p><h2>Gestão de pedidos</h2><p>Consulte pagamentos, dados de entrega e avance cada pedido pelo fluxo operacional.</p></div><Link href="/admin/pedidos">Abrir pedidos →</Link></section></>;
}
