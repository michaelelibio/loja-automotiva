'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCard } from '@/components/ProductCard';
import { useAuth } from '@/context/AuthContext';
import { useFavorites } from '@/context/FavoritesContext';

type AccountSection = 'dashboard' | 'orders' | 'favorites' | 'vehicles' | 'settings';
type SettingsSection = 'profile' | 'addresses' | 'security';

const accountSections: { id: AccountSection; label: string }[] = [
  { id: 'dashboard', label: 'Minha Garagem' },
  { id: 'orders', label: 'Pedidos' },
  { id: 'favorites', label: 'Favoritos' },
  { id: 'vehicles', label: 'Veículos' },
  { id: 'settings', label: 'Configurações' },
];

const settingsSections: { id: SettingsSection; label: string }[] = [
  { id: 'profile', label: 'Meus dados' },
  { id: 'addresses', label: 'Endereços' },
  { id: 'security', label: 'Segurança' },
];

export default function ContaPage() {
  const { user, isAuthenticated, isLoading } = useAuth();
  const { favorites, favoriteCount, isLoading: loadingFavorites, error: favoritesError } = useFavorites();
  const [activeSection, setActiveSection] = useState<AccountSection>('dashboard');
  const [settingsSection, setSettingsSection] = useState<SettingsSection>('profile');
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) router.replace('/login');
  }, [isLoading, isAuthenticated, router]);

  if (isLoading || !isAuthenticated) {
    return (
      <main>
        <Header />
        <div style={{ padding: 80, textAlign: 'center' }}>Carregando sua conta...</div>
        <Footer />
      </main>
    );
  }

  const firstName = user?.name ? user.name.split(' ')[0] : '';
  const loginMethod = user?.authProvider === 'GOOGLE' ? 'Google' : 'E-mail e senha';

  function selectSection(section: AccountSection) {
    setActiveSection(section);
  }

  return (
    <main>
      <Header />
      <div className="account-layout">
        <aside className="account-sidebar" aria-label="Navegação da conta">
          <nav>
            {accountSections.map((section) => (
              <button
                key={section.id}
                type="button"
                className={activeSection === section.id ? 'active' : ''}
                aria-current={activeSection === section.id ? 'page' : undefined}
                onClick={() => selectSection(section.id)}
              >
                {section.label}
              </button>
            ))}
          </nav>
        </aside>

        <section className="account-main" aria-live="polite">
          {activeSection === 'dashboard' && (
            <>
              <AccountHeading
                eyebrow="ÁREA DO CLIENTE"
                title="Minha Garagem."
                description={`Olá, ${firstName}. Seu espaço para acompanhar compras, salvar produtos e cuidar da sua garagem.`}
              />
              <div className="account-cards">
                <AccountCard index="01" title="Pedidos" description="Nenhum pedido ainda" action="Ver pedidos" onClick={() => selectSection('orders')} />
                <AccountCard index="02" title="Favoritos" description={`${favoriteCount} ${favoriteCount === 1 ? 'produto salvo' : 'produtos salvos'}`} action="Ver favoritos" onClick={() => selectSection('favorites')} />
                <AccountCard index="03" title="Veículos" description="Nenhum veículo cadastrado" action="Gerenciar veículos" onClick={() => selectSection('vehicles')} />
                <AccountCard index="04" title="Configurações" description="Gerencie seus dados, endereços e segurança" action="Abrir configurações" onClick={() => selectSection('settings')} />
              </div>
            </>
          )}

          {activeSection === 'orders' && (
            <AccountEmptyState eyebrow="PEDIDOS" title="Seus pedidos" description="Você ainda não possui pedidos." />
          )}

          {activeSection === 'favorites' && (
            <>
              <AccountHeading eyebrow="FAVORITOS" title="Produtos salvos" description="Confira os itens que você marcou para acessar depois." />
              <div className="account-panel account-favorites-panel">
                {loadingFavorites && <p className="account-status">Carregando seus favoritos...</p>}
                {favoritesError && <div className="api-error-banner account-api-error">{favoritesError}</div>}
                {!loadingFavorites && !favoritesError && favorites.length === 0 && (
                  <div className="empty-catalog">
                    <h2>Você ainda não salvou nenhum favorito</h2>
                    <p>Explore a loja e adicione produtos que você quer ver de novo.</p>
                    <Link className="text-link" href="/produtos">Ver produtos</Link>
                  </div>
                )}
                {favorites.length > 0 && (
                  <div className="products-grid catalog-grid">
                    {favorites.map((product) => <ProductCard key={product.id} product={product} />)}
                  </div>
                )}
              </div>
            </>
          )}

          {activeSection === 'vehicles' && (
            <AccountEmptyState eyebrow="VEÍCULOS" title="Minha garagem" description="Nenhum veículo cadastrado." action="Adicionar veículo" />
          )}

          {activeSection === 'settings' && (
            <>
              <AccountHeading eyebrow="CONFIGURAÇÕES" title="Sua conta" description="Consulte seus dados, endereços e informações de segurança." />
              <div className="account-settings">
                <nav className="settings-tabs" aria-label="Seções de configurações">
                  {settingsSections.map((section) => (
                    <button key={section.id} type="button" className={settingsSection === section.id ? 'active' : ''} aria-selected={settingsSection === section.id} onClick={() => setSettingsSection(section.id)}>
                      {section.label}
                    </button>
                  ))}
                </nav>

                <div className="settings-content">
                  {settingsSection === 'profile' && (
                    <div className="account-data-list">
                      <div><span>Nome</span><strong>{user?.name}</strong></div>
                      <div><span>E-mail</span><strong>{user?.email}</strong></div>
                      <div><span>Tipo de login</span><strong>{loginMethod}</strong></div>
                    </div>
                  )}
                  {settingsSection === 'addresses' && <AccountEmptyState eyebrow="ENDEREÇOS" title="Seus endereços" description="Nenhum endereço cadastrado." compact />}
                  {settingsSection === 'security' && (
                    <div className="account-data-list">
                      <div><span>Método de login</span><strong>{loginMethod}</strong></div>
                      <p className="account-note">As opções de segurança disponíveis dependem do método usado para acessar sua conta.</p>
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </section>
      </div>
      <Footer />
    </main>
  );
}

function AccountHeading({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <div className="account-hero"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div></div>;
}

function AccountCard({ index, title, description, action, onClick }: { index: string; title: string; description: string; action: string; onClick: () => void }) {
  return <div className="account-card"><div><div className="card-index">{index}</div><h3>{title}</h3><p>{description}</p></div><div className="card-action"><button type="button" onClick={onClick}>{action} →</button></div></div>;
}

function AccountEmptyState({ eyebrow, title, description, action, compact = false }: { eyebrow: string; title: string; description: string; action?: string; compact?: boolean }) {
  return <div className={`account-empty-state ${compact ? 'compact' : ''}`}><p className="eyebrow">{eyebrow}</p><h2>{title}</h2><p>{description}</p>{action && <button type="button" disabled>{action}</button>}</div>;
}
