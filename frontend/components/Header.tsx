'use client';

import Link from 'next/link';
import Image from 'next/image';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useCart } from '@/context/CartContext';
import { CartIcon, SearchIcon } from './Icons';
import { useAuth } from '@/context/AuthContext';

export function Header() {
  const { totalItems } = useCart();
  const { user, isAuthenticated, isLoading, sessionError, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const userToggleRef = useRef<HTMLButtonElement | null>(null);
  const logoutDialogRef = useRef<HTMLDivElement | null>(null);
  const logoutStartedRef = useRef(false);
  const pathname = usePathname();
  const router = useRouter();

  const displayName = user?.name ? user.name.split(' ')[0] : '';
  const protectedRoute = pathname === '/conta' || pathname.startsWith('/conta/')
    || pathname === '/checkout' || pathname === '/admin' || pathname.startsWith('/admin/');

  function requestLogout() {
    logoutStartedRef.current = false;
    setLoggingOut(false);
    setOpen(false);
    setConfirmingLogout(true);
  }

  function closeLogoutDialog() {
    if (loggingOut) return;
    setConfirmingLogout(false);
    requestAnimationFrame(() => userToggleRef.current?.focus());
  }

  function confirmLogout() {
    if (logoutStartedRef.current) return;
    logoutStartedRef.current = true;
    setLoggingOut(true);
    setOpen(false);
    logout();
    setConfirmingLogout(false);
    if (protectedRoute) router.replace('/');
  }

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!menuRef.current) return;
      if (!menuRef.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('click', onDocClick);
    return () => document.removeEventListener('click', onDocClick);
  }, [open]);

  useEffect(() => {
    if (confirmingLogout) logoutDialogRef.current?.focus();
  }, [confirmingLogout]);

  function handleDialogKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeLogoutDialog();
      return;
    }
    if (event.key !== 'Tab') return;
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not([disabled])'));
    if (buttons.length === 0) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === event.currentTarget)) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus();
    }
  }

  return (
    <header className="site-header">
      <Link className="brand" href="/" aria-label="GARAGE início">
        <span className="brand-mark">G</span>
        <span>GARAGE<span className="brand-dot">.</span></span>
      </Link>

      <nav className="main-nav" aria-label="Navegação principal">
        <Link href="/produtos">Produtos</Link>
        <Link href="/contato">Contato</Link>
      </nav>

      <div className="header-actions">
        <label className="search-box">
          <SearchIcon />
          <input type="search" placeholder="Buscar produto" aria-label="Buscar produto" />
        </label>

        {isLoading && (
          <span className="auth-loading" role="status" aria-label="Verificando autenticação">
            Conta
          </span>
        )}

        {!isLoading && isAuthenticated && (
          <div className="user-menu" ref={menuRef}>
            <button ref={userToggleRef} type="button" className="user-toggle" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((s) => !s)}>
              {user?.pictureUrl
                ? <Image src={user.pictureUrl} alt={user.name} className="user-avatar" width={32} height={32} unoptimized />
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
                  <Link href="/conta/veiculos">Veículos</Link>
                  <Link href="/conta/enderecos">Endereços</Link>
                </nav>
                <div className="account-dropdown-sep" />
                <button type="button" className="logout-button" onClick={requestLogout}>Sair</button>
              </div>
            )}
          </div>
        )}

        {!isLoading && !isAuthenticated && (
          <Link href="/login" className="auth-link" title={sessionError ?? undefined}>Entrar</Link>
        )}

        <Link className="cart-button" href="/carrinho" aria-label="Abrir carrinho" title="Carrinho">
          <CartIcon />
          <span className="cart-count">{totalItems}</span>
        </Link>
      </div>

      {confirmingLogout && (
        <div className="logout-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeLogoutDialog(); }}>
          <div
            ref={logoutDialogRef}
            className="logout-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="logout-dialog-title"
            aria-describedby="logout-dialog-description"
            tabIndex={-1}
            onKeyDown={handleDialogKeyDown}
          >
            <span className="logout-dialog-mark" aria-hidden="true">G</span>
            <h2 id="logout-dialog-title">Sair da sua conta?</h2>
            <p id="logout-dialog-description">Tem certeza de que deseja sair? Seu carrinho continuará salvo neste dispositivo.</p>
            <div className="logout-dialog-actions">
              <button type="button" onClick={closeLogoutDialog} disabled={loggingOut}>Cancelar</button>
              <button type="button" className="confirm" onClick={confirmLogout} disabled={loggingOut}>{loggingOut ? 'Saindo...' : 'Sair'}</button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
