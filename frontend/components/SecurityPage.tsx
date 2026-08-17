import Link from 'next/link';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';

export function SecurityPage({ eyebrow, title, description, children }: { eyebrow: string; title: string; description: string; children: React.ReactNode }) {
  return <main><Header /><section className="security-page"><div className="security-intro"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p><Link href="/">← Voltar para a GARAGE</Link></div><div className="security-card">{children}</div></section><Footer /></main>;
}
