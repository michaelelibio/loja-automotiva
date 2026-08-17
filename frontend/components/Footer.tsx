import Link from 'next/link';

const footerGroups = [
  { label: 'Institucional', links: [{ href: '/contato', text: 'Contato' }] },
  { label: 'Ajuda', links: [{ href: '/entrega', text: 'Entrega' }, { href: '/trocas-e-devolucoes', text: 'Trocas e devoluções' }] },
  { label: 'Legal', links: [{ href: '/privacidade', text: 'Política de Privacidade' }, { href: '/termos', text: 'Termos de Uso' }] },
];

export function Footer() {
  return (
    <footer className="site-footer">
      <div>
        <Link className="brand footer-brand" href="/" aria-label="GARAGE início"><span className="brand-mark">G</span><span>GARAGE<span className="brand-dot">.</span></span></Link>
        <p>Para quem gosta de carro<br />bem cuidado.</p>
      </div>
      <nav className="footer-links" aria-label="Links do rodapé">
        {footerGroups.map((group) => <div key={group.label}><span className="footer-label">{group.label}</span>{group.links.map((link) => <Link href={link.href} key={link.href}>{link.text}</Link>)}</div>)}
      </nav>
      <div className="footer-bottom"><span>© GARAGE</span><span>Feito para a estrada.</span></div>
    </footer>
  );
}
