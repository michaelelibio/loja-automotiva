import { CartIcon, SearchIcon } from './Icons';

export function Header() {
  return (
    <header className="site-header">
      <a className="brand" href="#top" aria-label="GARAGE início">
        <span className="brand-mark">G</span>
        <span>GARAGE<span className="brand-dot">.</span></span>
      </a>

      <nav className="main-nav" aria-label="Navegação principal">
        <a href="#produtos">Produtos</a>
        <a href="#kits">Kits</a>
        <a href="#contato">Contato</a>
      </nav>

      <div className="header-actions">
        <label className="search-box">
          <SearchIcon />
          <input type="search" placeholder="Buscar produto" aria-label="Buscar produto" />
        </label>
        <button className="cart-button" type="button" aria-label="Abrir carrinho" title="Carrinho">
          <CartIcon />
          <span className="cart-count">0</span>
        </button>
      </div>
    </header>
  );
}