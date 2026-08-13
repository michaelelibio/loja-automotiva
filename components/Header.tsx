'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { useCart } from '@/context/CartContext';
import { CartIcon, SearchIcon } from './Icons';
import { useAuth } from '@/context/AuthContext';

export function Header() {
  const { totalItems } = useCart();
  const { user, isAuthenticated, isLoading, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const displayName = user?.name ? user.name.split(' ')[0] : '';

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!menuRef.current) return;
      if (!menuRef.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('click', onDocClick);
    return () => document.removeEventListener('click', onDocClick);
  }, [open]);

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

        {!isLoading && !isAuthenticated && (
          <Link href="/login" className="auth-link">Entrar</Link>
        )}

        {!isLoading && isAuthenticated && (
          <div className="user-menu" ref={menuRef}>
            <button type="button" className="user-toggle" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((s) => !s)}>
              {user?.pictureUrl
                ? <img src={user.pictureUrl} alt={user.name} className="user-avatar" />
                : <span className="user-initial">{displayName ? displayName.charAt(0).toUpperCase() : 'U'}</span>
              }
              <span className="user-name">{displayName}</span>
              <span className="user-caret">▾</span>
            </button>

            {open && (
              <div className="account-dropdown" role="menu" aria-label="Menu do usuário">
                <div className="account-dropdown-header">MINHA GARAGEM</div>
                <nav className="account-dropdown-list">
                  <Link href="/conta">Minha conta</Link>
                  <Link href="/conta/favoritos">Favoritos</Link>
                  <Link href="/conta/pedidos">Meus pedidos</Link>
                  <Link href="/conta/veiculos">Endereços</Link>
                  <Link href="/conta/enderecos">Endereços</Link>
                </nav>
                <div className="account-dropdown-sep" />
                <button type="button" className="logout-button" onClick={() => { setOpen(false); logout(); }}>Sair</button>
              </div>
            )}
          </div>
        )}

        <Link className="cart-button" href="/carrinho" aria-label="Abrir carrinho" title="Carrinho">
          <CartIcon />
          <span className="cart-count">{totalItems}</span>
        </Link>
      </div>
    </header>
  );
}