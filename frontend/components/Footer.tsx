import Link from 'next/link';
import { BrandLogo } from './BrandLogo';

const footerGroups = [
  { label: 'Institucional', links: [{ href: '/contato', text: 'Contato' }] },
  { label: 'Ajuda', links: [{ href: '/entrega', text: 'Entrega' }, { href: '/trocas-e-devolucoes', text: 'Trocas e devoluções' }] },
  { label: 'Legal', links: [{ href: '/privacidade', text: 'Política de Privacidade' }, { href: '/termos', text: 'Termos de Uso' }] },
];

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <div>
          <Link className="brand footer-brand" href="/" aria-label="inGarage — página inicial"><BrandLogo variant="light" /></Link>
          <p>Produtos automotivos para quem faz do cuidado um ritual.</p>
        </div>
        <nav className="footer-links" aria-label="Links do rodapé">
          {footerGroups.map((group) => <div key={group.label}><span className="footer-label">{group.label}</span>{group.links.map((link) => <Link href={link.href} key={link.href}>{link.text}</Link>)}</div>)}
        </nav>
        <div className="footer-bottom"><span>© 2026 inGarage. Todos os direitos reservados.</span><span>Seu carro. <strong>Seu ritual.</strong></span></div>
      </div>
    </footer>
  );
}
