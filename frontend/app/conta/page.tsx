'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { ProductCard } from '@/components/ProductCard';
import { VehiclesPanel } from '@/components/VehiclesPanel';
import { AddressesPanel } from '@/components/AddressesPanel';
import { OrdersPanel } from '@/components/OrdersPanel';
import { ChangePasswordForm } from '@/components/ChangePasswordForm';
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
  const { user, isAuthenticated, isLoading, sessionError, updateProfile, logout } = useAuth();
  const { favorites, favoriteCount, isLoading: loadingFavorites, error: favoritesError } = useFavorites();
  const [activeSection, setActiveSection] = useState<AccountSection>('dashboard');
  const [settingsSection, setSettingsSection] = useState<SettingsSection>('profile');
  const [name, setName] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [vehicleCount, setVehicleCount] = useState<number | null>(null);
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [isLoading, sessionError, isAuthenticated, router]);

  useEffect(() => {
    // O perfil chega após a restauração assíncrona da sessão e precisa preencher o formulário.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(user?.name ?? '');
  }, [user?.name]);

  useEffect(() => {
    function applyHash() {
      if (window.location.hash === '#addresses') { setActiveSection('settings'); setSettingsSection('addresses'); }
    }
    applyHash(); window.addEventListener('hashchange', applyHash);
    return () => window.removeEventListener('hashchange', applyHash);
  }, []);

  const handleVehicleCountChange = useCallback((count: number) => setVehicleCount(count), []);

  if (isLoading || sessionError || !isAuthenticated) {
    return (
      <main>
        <Header />
        <div style={{ padding: 80, textAlign: 'center' }}>{sessionError ?? 'Carregando sua conta...'}</div>
        <Footer />
      </main>
    );
  }

  const firstName = user?.name ? user.name.split(' ')[0] : '';
  const loginMethod = user?.authProvider === 'GOOGLE' ? 'Google' : 'E-mail e senha';

  function selectSection(section: AccountSection) {
    setActiveSection(section);
  }

  async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setSaveStatus('idle');
    const result = await updateProfile({ name });
    setSaveStatus(result.success ? 'success' : 'error');
    setIsSaving(false);
  }

  function handleLogout() {
    logout();
    router.push('/');
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
                <AccountCard index="03" title="Veículos" description={vehicleCount === null ? 'Consulte sua garagem' : `${vehicleCount} ${vehicleCount === 1 ? 'veículo cadastrado' : 'veículos cadastrados'}`} action="Gerenciar veículos" onClick={() => selectSection('vehicles')} />
                <AccountCard index="04" title="Configurações" description="Gerencie seus dados, endereços e segurança" action="Abrir configurações" onClick={() => selectSection('settings')} />
              </div>
            </>
          )}

          {activeSection === 'orders' && (
            <><AccountHeading eyebrow="PEDIDOS" title="Seus pedidos" description="Acompanhe suas compras e consulte todos os detalhes." /><div className="account-panel"><OrdersPanel compact /></div></>
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
            <VehiclesPanel onCountChange={handleVehicleCountChange} />
          )}

          {activeSection === 'settings' && (
            <>
              <AccountHeading eyebrow="CONFIGURAÇÕES" title="Sua conta" description="Consulte seus dados, endereços e informações de segurança." />
              <div className="account-settings">
                <nav className="settings-tabs" aria-label="Seções de configurações" role="tablist">
                  {settingsSections.map((section) => (
                    <button key={section.id} type="button" role="tab" className={settingsSection === section.id ? 'active' : ''} aria-selected={settingsSection === section.id} onClick={() => setSettingsSection(section.id)}>
                      {section.label}
                    </button>
                  ))}
                </nav>

                <div className="settings-content">
                  {settingsSection === 'profile' && (
                    <form className="account-profile-form" onSubmit={handleProfileSubmit}>
                      <label>
                        <span>Nome</span>
                        <input value={name} onChange={(event) => { setName(event.target.value); setSaveStatus('idle'); }} minLength={2} maxLength={150} required />
                      </label>
                      <label>
                        <span>E-mail</span>
                        <input value={user?.email ?? ''} readOnly aria-readonly="true" />
                      </label>
                      <div className="account-readonly-row"><span>Tipo de login</span><strong>{loginMethod}</strong></div>
                      <div className="account-readonly-row"><span>Status do e-mail</span><strong className={user?.emailVerified ? 'email-verified' : 'email-unverified'}>{user?.emailVerified ? 'E-mail verificado' : 'E-mail ainda não verificado'}</strong></div>
                      <div className="account-form-actions">
                        <button type="submit" disabled={isSaving || !name.trim()}>{isSaving ? 'Salvando...' : 'Salvar alterações'}</button>
                        {saveStatus === 'success' && <p className="account-form-feedback success" role="status">Alterações salvas.</p>}
                        {saveStatus === 'error' && <p className="account-form-feedback error" role="alert">Não foi possível salvar as alterações.</p>}
                      </div>
                    </form>
                  )}
                  {settingsSection === 'addresses' && <AddressesPanel />}
                  {settingsSection === 'security' && (
                    <div className="account-security">
                      <div className="account-data-list">
                        <div><span>Método de login</span><strong>{loginMethod}</strong></div>
                        {user?.authProvider === 'LOCAL' && <div><span>Senha</span><strong>••••••••</strong></div>}
                      </div>
                      {user?.authProvider === 'LOCAL' ? (
                        <ChangePasswordForm />
                      ) : (
                        <p className="account-note">A senha desta conta é gerenciada pelo Google.</p>
                      )}
                      <div className="account-session">
                        <div><strong>Sessão</strong><p>Encerre o acesso neste dispositivo.</p></div>
                        <button type="button" onClick={handleLogout}>Sair da conta</button>
                      </div>
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
