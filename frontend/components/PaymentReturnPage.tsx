import Link from 'next/link';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';

export function PaymentReturnPage({ eyebrow, title, description }: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return <main>
    <Header />
    <section className="account-route-state">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p>{description}</p>
      <Link href="/conta/pedidos">Acompanhar meus pedidos</Link>
    </section>
    <Footer />
  </main>;
}
