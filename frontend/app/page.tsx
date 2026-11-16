import Link from 'next/link';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';
import { ProductCard } from '@/components/ProductCard';
import { ArrowUpRightIcon } from '@/components/Icons';
import { fetchCatalog, fetchCategories, type CatalogResult } from '@/lib/catalog';
import type { Category } from '@/lib/catalog-types';

function HomeHero() {
  return <section className="home-hero" aria-labelledby="hero-title">
    <div className="home-hero__inner">
      <div className="home-hero__copy">
        <p className="eyebrow">Cuidado que se sente na estrada</p>
        <h1 id="hero-title">Seu carro.<br /><em>Seu ritual.</em></h1>
        <p>Produtos de alta performance para transformar o cuidado automotivo em parte do seu dia.</p>
        <Link className="home-cta" href="/produtos">Explorar produtos <ArrowUpRightIcon /></Link>
        <div className="home-hero__facts" aria-label="Informações da loja"><span>Compra segura</span><span>Entrega para todo o Brasil</span><span>Produtos selecionados</span></div>
      </div>
    </div>
    <div className="home-hero__visual" role="img" aria-label="Carro esportivo branco com faixas laranja em uma estrada" />
  </section>;
}

function BenefitsBar() {
  const benefits = [
    ['truck', 'Envio para todo o Brasil', 'Consulte opções para sua região'],
    ['shield', 'Compra segura', 'Seus dados protegidos'],
    ['spark', 'Produtos selecionados', 'Cuidado em cada detalhe'],
    ['support', 'Fale com a gente', 'Atendimento pelo contato'],
  ] as const;
  return <section className="home-benefits" aria-label="Benefícios">
    {benefits.map(([icon, title, description]) => <div key={title}><BenefitIcon kind={icon} /><div><strong>{title}</strong><small>{description}</small></div></div>)}
  </section>;
}

function BenefitIcon({ kind }: { kind: 'truck' | 'shield' | 'spark' | 'support' }) {
  const paths = {
    truck: <><path d="M2 7h12v10H2zM14 10h4l4 4v3h-8z" /><circle cx="6" cy="18" r="2" /><circle cx="18" cy="18" r="2" /></>,
    shield: <><path d="M12 2 20 6v6c0 5-3.5 8-8 10-4.5-2-8-5-8-10V6z" /><path d="m8 12 3 3 5-6" /></>,
    spark: <><path d="m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z" /></>,
    support: <><path d="M3 13v-2a9 9 0 0 1 18 0v2M3 13v5h4v-5H3Zm14 0v5h4v-5h-4ZM17 19c-1 2-3 3-6 3" /></>,
  };
  return <svg className="home-benefit-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[kind]}</svg>;
}

function FeaturedCategories({ categories }: { categories: Category[] }) {
  if (!categories.length) return null;
  return <section className="home-categories" id="categorias" aria-labelledby="categories-title">
    <div className="home-section-heading"><h2 id="categories-title">Categorias em destaque</h2><Link href="/produtos">Ver todas as categorias <span aria-hidden="true">→</span></Link></div>
    <div className="home-category-grid">{categories.filter((c) => c.parentId === null).map((category) => <Link className="home-category" key={category.id} href={`/categorias/${category.path}`}>
      <span>{category.name}</span><span className="home-category__arrow" aria-hidden="true">→</span>
    </Link>)}</div>
  </section>;
}

function ProductShelf({ result, section, title }: { result: CatalogResult | null; section: string; title: string }) {
  if (!result?.content.length) return null;
  return <section className="products-section home-products" id={`vitrine-${section}`} aria-labelledby={`title-${section}`}>
    <div className="section-heading"><div><p className="eyebrow">Seleção inGarage</p><h2 id={`title-${section}`}>{title}</h2></div><Link className="text-link" href={`/produtos?section=${section}`}>Ver todos <ArrowUpRightIcon /></Link></div>
    <div className="products-grid">{result.content.map((product) => <ProductCard key={product.id} product={product} />)}</div>
  </section>;
}

function PerformanceBanner() {
  return <section className="performance-banner" id="sobre" aria-labelledby="performance-title">
    <div className="performance-banner__image" role="img" aria-label="Carro esportivo em ambiente escuro" />
    <div className="performance-banner__copy"><p className="eyebrow">Seu carro. Seu ritual.</p><h2 id="performance-title">Mais que peças.<br /><em>É performance.</em></h2><p>Cada detalhe transforma a experiência de cuidar do seu carro.</p><Link className="home-cta" href="/produtos">Ver produtos <ArrowUpRightIcon /></Link></div>
  </section>;
}

export default async function Home() {
  const sections = [['featured', 'Produtos em destaque'], ['beginner', 'Para começar'], ['professional', 'Uso profissional'], ['newest', 'Novidades'], ['bestsellers', 'Mais vendidos']];
  const [categories, ...results] = await Promise.all([fetchCategories().catch(() => []), ...sections.map(([section]) => fetchCatalog({ section, size: '6' }).catch(() => null))]);
  return <main id="top"><div className="top-trust"><span>Envio para todo o Brasil</span><span>Compra segura</span><span className="top-trust__motto">Potência nos detalhes</span></div><Header /><HomeHero /><BenefitsBar /><FeaturedCategories categories={categories as Category[]} />{results.every((r) => r === null) && <p className="api-error-banner" role="alert">Não foi possível carregar o catálogo. Tente novamente em instantes.</p>}{sections.map(([section,title], i) => <ProductShelf key={section} section={section} title={title} result={results[i] as CatalogResult | null} />)}<PerformanceBanner /><Footer /></main>;
}
