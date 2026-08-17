import type { ReactNode } from 'react';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';

export type InstitutionalSection = {
  title: string;
  content: ReactNode;
};

export function InstitutionalPage({ eyebrow, title, introduction, sections, reviewNotice = false }: {
  eyebrow: string;
  title: string;
  introduction: string;
  sections: InstitutionalSection[];
  reviewNotice?: boolean;
}) {
  return (
    <main>
      <Header />
      <article className="institutional-page">
        <header className="institutional-hero">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p>{introduction}</p>
        </header>
        {reviewNotice && (
          <aside className="institutional-review" aria-label="Pendência antes da publicação">
            <strong>REVISÃO NECESSÁRIA</strong>
            <p>[DADOS EMPRESARIAIS E CANAL DE ATENDIMENTO A DEFINIR ANTES DA PUBLICAÇÃO]</p>
          </aside>
        )}
        <div className="institutional-content">
          {sections.map((section, index) => (
            <section key={section.title}>
              <span aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <div><h2>{section.title}</h2>{section.content}</div>
            </section>
          ))}
        </div>
      </article>
      <Footer />
    </main>
  );
}
