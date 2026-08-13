export function Footer() {
  return (
    <footer className="site-footer" id="contato">
      <div>
        <a className="brand footer-brand" href="#top"><span className="brand-mark">G</span><span>GARAGE<span className="brand-dot">.</span></span></a>
        <p>Para quem gosta de carro<br />bem cuidado.</p>
      </div>
      <div className="footer-links">
        <div><span className="footer-label">Fale com a gente</span><a href="mailto:oi@garage.com.br">oi@garage.com.br</a></div>
        <div><span className="footer-label">Acompanhe</span><a href="#instagram">Instagram</a><a href="#tiktok">TikTok</a></div>
      </div>
      <div className="footer-bottom"><span>© 2024 GARAGE</span><span>Feito para a estrada.</span></div>
    </footer>
  );
}