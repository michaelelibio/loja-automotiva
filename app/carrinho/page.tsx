import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { CartView } from './CartView';

export default function CartPage() {
  return (
    <main id="top">
      <Header />
      <section className="cart-intro" aria-labelledby="cart-title">
        <p className="eyebrow">Seu ritual continua</p>
        <h1 id="cart-title">Carrinho</h1>
      </section>
      <CartView />
      <Footer />
    </main>
  );
}