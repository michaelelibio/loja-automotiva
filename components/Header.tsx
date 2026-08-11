'use client';

import Link from 'next/link';
import { useCart } from '@/context/CartContext';
import { CartIcon, SearchIcon } from './Icons';

export function Header() {
  const { totalItems } = useCart();

  return (
    <header className="site-header">
      <Link className="brand" href="/" aria-label="GARAGE início">
        <span className="brand-mark">G</span>
        <span>GARAGE<span className="brand-dot">.</span></span>
      </Link>

      <nav className="main-nav" aria-label="Navegação principal">
        <Link href="/produtos">Produtos</Link>
        <Link href="/#kits">Kits</Link>
        <Link href="/#contato">Contato</Link>
      </nav>

      <div className="header-actions">
        <label className="search-box">
          <SearchIcon />
          <input type="search" placeholder="Buscar produto" aria-label="Buscar produto" />
        </label>
        <Link className="cart-button" href="/carrinho" aria-label="Abrir carrinho" title="Carrinho">
          <CartIcon />
          <span className="cart-count">{totalItems}</span>
        </Link>
      </div>
    </header>
  );
}