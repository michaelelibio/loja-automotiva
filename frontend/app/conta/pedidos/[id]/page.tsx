'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { OrderStatusBadge, formatOrderDate } from '@/components/OrdersPanel';
import { MercadoPagoPayment } from '@/components/MercadoPagoPayment';
import { useAuth } from '@/context/AuthContext';
import { acceptOrderQuote, getOrder } from '@/lib/api/orders';
import type { Order, OrderStatus } from '@/lib/types/order';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const zip = (value: string) => value.replace(/\D/g, '').replace(/^(\d{5})(\d{3})$/, '$1-$2');
const statusContent: Record<OrderStatus, { title: string; description: string }> = {
  PENDING_PAYMENT: { title: 'Aguardando pagamento', description: 'Seu pedido foi criado e está aguardando a confirmação do pagamento.' },
  PAID: { title: 'Pagamento aprovado', description: 'O pagamento do seu pedido foi aprovado.' },
  PROCESSING: { title: 'Preparando pedido', description: 'Seu pedido está sendo preparado.' },
  SHIPPED: { title: 'Enviado', description: 'Seu pedido foi enviado.' },
  DELIVERED: { title: 'Entregue', description: 'Seu pedido foi entregue.' },
  CANCELED: { title: 'Cancelado', description: 'Este pedido foi cancelado.' },
  EXPIRED: { title: 'Expirado', description: 'O prazo para pagamento deste pedido expirou.' },
};

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, sessionError } = useAuth();
  const [order, setOrder] = useState<Order | null>(null);
  const [quoteExpired, setQuoteExpired] = useState(false);
  const [accepting, setAccepting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const orderId = Number(params.id);

  useEffect(() => {
    if (!authLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [authLoading, sessionError, isAuthenticated, router]);

  useEffect(() => {
    if (authLoading || sessionError || !isAuthenticated) return;
    if (!Number.isSafeInteger(orderId)) return;
    let active = true;
    getOrder(orderId)
      .then((data) => { if (active) setOrder(data); })
      .catch(() => { if (active) setError('Não foi possível carregar este pedido.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [authLoading, sessionError, isAuthenticated, orderId]);

  const quoteValidUntil = order?.quote?.validUntil;
  useEffect(() => {
    if (!quoteValidUntil) return;
    const timer = setInterval(() => setQuoteExpired(Date.now() >= Date.parse(quoteValidUntil)), 1000);
    return () => clearInterval(timer);
  }, [quoteValidUntil]);

  if (!Number.isSafeInteger(orderId)) {
    return <main><Header /><div className="account-route-state"><p>Pedido inválido.</p><Link href="/conta/pedidos">Voltar aos pedidos</Link></div><Footer /></main>;
  }

  if (authLoading || sessionError || !isAuthenticated || loading) {
    return <main><Header /><div className="account-route-state">{sessionError ?? 'Carregando pedido...'}</div><Footer /></main>;
  }
  if (error || !order) {
    return <main><Header /><div className="account-route-state"><p>{error}</p><Link href="/conta/pedidos">Voltar aos pedidos</Link></div><Footer /></main>;
  }

  const address = order.shippingAddress;
  const currentStatus = statusContent[order.status];

  return <main>
    <Header />
    <section className="account-route-intro order-detail-heading">
      <div><p className="eyebrow">PEDIDO #{order.id}</p><h1>Detalhes do pedido</h1><p>{formatOrderDate(order.createdAt)}</p></div>
      <OrderStatusBadge status={order.status} />
    </section>
    <section className="order-detail-content">
      <div className="order-detail-main">
        <section className={`order-detail-card order-progress ${order.status.toLowerCase()}`}>
          <p className="eyebrow">Status do pedido</p>
          <h2>{order.quote?.state === 'WAITING' ? 'Aguardando cotação' : order.quote?.state === 'CONFIRMED' ? 'Aguardando seu aceite' : currentStatus.title}</h2>
          <p>{order.quote?.state === 'WAITING' || order.quote?.state === 'CONFIRMED' ? 'Confira a cotação abaixo antes de pagar.' : currentStatus.description}</p>
        </section>
        {order.quote && <section className="order-detail-card">
          <h2>Cotação AliExpress</h2>
          <p>{({ WAITING:'Aguardando conferência do preço completo e frete para seu endereço. Nenhuma cobrança foi liberada.', CONFIRMED:'Total confirmado. Confira as remessas e aceite antes de pagar.', ACCEPTED:'Total aceito. Pague dentro da validade informada.', REFUND_REQUIRED:'Identificamos mudança de custo. O envio está bloqueado e nossa equipe providenciará o cancelamento e reembolso integral.', REFUNDED:'Reembolso integral registrado e pedido cancelado.' } as Record<string,string>)[order.quote.state]}</p>
          {order.quote.validUntil && <p>Validade: {formatOrderDate(order.quote.validUntil)}</p>}
          {order.shipping.legs.map((leg,index)=><p key={index}>Remessa {index+1}: {leg.name} — {currency.format(leg.priceBrl)} — até {leg.estimatedDays} dias</p>)}
          {order.quote.state === 'CONFIRMED' && <button disabled={accepting || !order.quote.validUntil || quoteExpired} onClick={async()=>{
            setAccepting(true);setQuoteError(null);
            try {setOrder(await acceptOrderQuote(order.id,order.quote!.revision,order.total));}
            catch(cause){setQuoteError(cause instanceof Error ? cause.message : 'Não foi possível aceitar.');}
            finally{setAccepting(false);}
          }}>Aceitar total de {currency.format(order.total)}</button>}
          {quoteExpired && order.status === 'PENDING_PAYMENT' && <p role="alert">Cotação expirada. Solicite um novo pedido para reconfirmar o total.</p>}
          {quoteError && <p role="alert">{quoteError}</p>}
        </section>}
        {order.status === 'PENDING_PAYMENT' && (!order.quote || (order.quote.state === 'ACCEPTED' && !quoteExpired)) && <MercadoPagoPayment orderId={order.id} />}
        <section className="order-detail-card order-items-card">
          <h2>Itens</h2>
          <div className="order-detail-items">
            {order.items.map((item) => <article className="order-detail-item" key={`${item.productId}:${item.productVariantId ?? ''}`}>
              <div className="order-item-info">
                {item.productSlug ? <Link href={`/produtos/${item.productSlug}`}>{item.productName}</Link> : <strong>{item.productName}</strong>}
                {item.variantName && <span>{item.variantName}</span>}
                <span>Quantidade: {item.quantity}</span>
                <span>{currency.format(item.unitPrice)} cada</span>
              </div>
              <strong className="order-item-subtotal">{currency.format(item.subtotal)}</strong>
            </article>)}
          </div>
        </section>
        <section className="order-detail-card order-address-card">
          <h2>Endereço de entrega</h2>
          <address>
            <strong>{address.recipientName}</strong>
            <span>{address.street}, {address.number}</span>
            {address.complement && <span>{address.complement}</span>}
            <span>{address.neighborhood}</span>
            <span>{address.city} - {address.state}</span>
            <span>CEP {zip(address.zipCode)}</span>
          </address>
        </section>
        <section className="order-detail-card order-shipping-card">
          <p className="eyebrow">ENTREGA</p><h2>{order.shipping.name}</h2>
          <div><span>Valor do frete</span><strong>{order.quote?.state === 'WAITING' ? 'A confirmar' : currency.format(order.shipping.price)}</strong></div>
          {order.quote?.state === 'WAITING' ? <p>Prazo a confirmar.</p> : <p>Prazo estimado: até {order.shipping.estimatedDays} {order.shipping.estimatedDays === 1 ? 'dia' : 'dias'}.</p>}
        </section>
      </div>
      <aside className="order-totals">
        <h2>Resumo</h2>
        <div><span>Subtotal</span><strong>{currency.format(order.subtotal)}</strong></div>
        <div><span>Frete</span><strong>{order.quote?.state === 'WAITING' ? 'A confirmar' : order.shippingCost === 0 ? 'Grátis' : currency.format(order.shippingCost)}</strong></div>
        <div className="total"><span>Total</span><strong>{order.quote?.state === 'WAITING' ? 'A confirmar' : currency.format(order.total)}</strong></div>
        <Link href="/conta/pedidos">← Voltar aos pedidos</Link>
      </aside>
    </section>
    <Footer />
  </main>;
}
