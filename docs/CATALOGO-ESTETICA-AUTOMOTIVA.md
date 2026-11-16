# Catálogo de estética automotiva

A entrega original foi local, sem alterações de produção. Em 16/11/2026, a migração foi aplicada em produção e o backend recuperado no mesmo commit publicado, conforme [relatório da recuperação](RECUPERACAO-CATALOGO-20261116.md).

## Estrutura e compatibilidade

- Oito categorias principais e 40 subcategorias, com descrição e ordem conforme a especificação.
- URLs novas: `/categorias/{categoria}` e `/categorias/{categoria}/{subcategoria}`. Slug e vínculo ao pai permanecem estáveis após criar. Nome, descrição, ordem e ativação são editáveis.
- `/produtos`, `/produtos/{slug}` e `/api/products` continuam disponíveis. Os parâmetros antigos `busca` e `categoria` continuam aceitos; `categoria` consulta a classificação textual anterior.
- A categoria textual anterior permanece em `products.category`. A taxonomia usa relacionamentos separados. Nenhum produto existente é reclassificado pelo seed.
- Campos novos: categoria/subcategoria, marca, superfícies, finalidades, termos de busca, uso, concentrado/refil com estado desconhecido, destaque e Para começar, seleção comercial e primeira publicação conhecida.
- Nacional e Internacional são seleções comerciais manuais: Nacional exige fornecedor AliExpress; Internacional exige CJ. Não representam fabricação, armazém ou país de envio.
- As regras existentes de exposição pública e checkout por perfil são mantidas. Em `controlled`/produção, a política atual continua limitada a AliExpress dropshipping. Classificar CJ no ADM não desbloqueia checkout CJ. No perfil isolado `local-freight`, os fluxos anteriores de estoque local e CJ permanecem disponíveis.
- Stock, vínculos de fornecedor, variantes, frete e regras financeiras não são reescritos pela classificação. Edição administrativa e classificação em lote usam bloqueio do produto. O lote altera somente os campos selecionados e é transacional.
- Campos técnicos ausentes permanecem vazios. A normalização do frontend não usa informações comerciais ou técnicas dos produtos de exemplo.

## API e administração

`GET /api/catalog` retorna produtos paginados e facetas da seleção completa. Aceita `q`, `category`, `subcategory`, `legacyCategory`, `brand`, `surface`, `purpose`, `minPrice`, `maxPrice`, `usage`, `origin`, `concentrated`, `refill`, `type`, `section`, `sort`, `page` e `size` (máximo 100). Busca ignora acentos e trata curingas SQL como texto. Ordenações: relevância, menor/maior preço e primeira publicação, com datas desconhecidas no fim.

`GET /api/catalog/categories` oculta categorias vazias ou inativas, inclusive filhos de categoria inativa. A desativação não exclui nem desativa os produtos: eles continuam acessíveis no catálogo geral quando elegíveis. `GET /api/catalog/categories/resolve?path=...` resolve uma URL estável ativa.

O ADM inclui `/admin/categorias`, campos de classificação no cadastro/edição e seleção em lote na lista de produtos, inclusive após importar. Os endpoints `/api/admin/catalog/**` exigem ADMIN. Não há exclusão de categorias com produtos.

Vitrines: destaque e Para começar por marcação manual; profissional por uso PROFISSIONAL ou AMBOS; novidades por primeira publicação conhecida; mais vendidos por quantidade vendida em pedidos confirmados e sem revisão financeira, com pagamento aprovado, data de aprovação, verificação do provedor e modo real. Testes, pendências e revisões não alimentam popularidade. Vitrines vazias são ocultas.

## Migração antes de uma futura publicação

Arquivo: `backend/src/main/resources/db/migration/V20261116_01__detailing_catalog.sql`.

SHA-256: `76d407cbac5404f8cc0a9115c9436bcbc93d1ede4f1dfe661b684e3578cacdb4`.

O projeto utiliza SQL manual; não há adoção de Flyway ou Liquibase nesta alteração. A lista de inicialização SQL local inclui a migração; produção com inicialização desativada exige aplicação manual antes de iniciar o novo backend com Hibernate `validate`.

1. Fazer backup recuperável e registrar contagens e vínculos existentes.
2. Revisar o schema de destino e a migração. Ela pressupõe o schema anterior já compatível com o backend atual.
3. Em janela controlada, aplicar dentro de transação com `ON_ERROR_STOP`, verificar colunas, FKs, índices, 8 raízes/40 filhos e preservação dos dados antes do COMMIT. Não utilizar Hibernate `update` para essa publicação.
4. Publicar o backend; confirmar Hibernate `validate` e GETs. Publicar o frontend compatível depois.
5. Classificar os produtos manualmente a partir de informações confirmadas. Categorias vazias continuarão ocultas até terem produtos elegíveis.

O SQL é aditivo e o seed usa `ON CONFLICT(path) DO NOTHING`, sem sobrescrever edições administrativas. Não faz backfill de classificação, origem comercial ou data de publicação de produtos históricos. Após COMMIT, recuperar preferencialmente o código anterior mantendo o schema aditivo; não apagar colunas/classificações como rollback improvisado. Em caso de falha antes do COMMIT, executar ROLLBACK.

## Verificação local reproduzível

- `mvn verify`: suíte backend com H2 e APIs simuladas; testes operacionais externos são opt-in e ficam desativados.
- `npm test`, `npm run lint`, `node node_modules/typescript/bin/tsc --noEmit --incremental false`, `npm run build` no frontend.
- `backend/src/test/resources/catalog-migration-check.sql`: executar via `psql -X -v ON_ERROR_STOP=1 -f ...` exclusivamente em banco descartável. O próprio script testa reexecução, preservação e alterações administrativas e termina em ROLLBACK.
- `CatalogPostgresMigrationTests`: teste opt-in `INGARAGE_CATALOG_POSTGRES=true`, PostgreSQL descartável em `127.0.0.1:55461`, usuário `catalog_test`, banco `postgres`, schema dedicado `catalog_verify` previamente criado. Cria e remove tabelas somente nesse schema de teste. Simula colunas antigas, migra duas vezes, faz COMMIT e valida todos os mapeamentos Hibernate.
- Conferência de navegador usa apenas API sintética local, com tamanhos desktop 1440 px e celulares 390/320 px. Não usa produtos, tokens ou pagamentos reais.

## Pendências para disponibilização

A migração de produção foi aplicada e o backend validado em 16/11/2026. Cadastrar as classificações e informações técnicas confirmadas dos produtos reais. A publicação comercial CJ no perfil `controlled` depende de uma mudança própria nas políticas de checkout/frete existentes; não foi liberada pela implementação ou recuperação do catálogo. A publicação do frontend não foi alterada na recuperação.

## Arquivos principais alterados

- Backend: pacote novo `catalog/`, entidade/repositório de categorias, serviço e controladores públicos/administrativos; `Product`, `ProductService`, `ProductResponse`, `AdminProductService`, DTOs administrativos e `SecurityConfig`.
- Banco: migração acima e lista local de scripts em `application.properties`.
- Frontend: tipos/helpers de catálogo, rotas de categoria/subcategoria e catálogo paginado, página inicial e suas cinco vitrines, Header/menu, cartão e detalhes do produto, filtros responsivos, formulário administrativo, edição em lote, página de categorias e estilos.
- Compatibilidade: `lib/products.ts` preserva campos reais sem fallback fictício e percorre páginas da API para carrinho/checkout/favoritos.
- Testes novos: `CatalogIntegrationTests.java`, `CatalogPostgresMigrationTests.java`, `catalog-migration-check.sql` e `frontend/tests/catalog.test.cjs`.

## Resultados verificados em 16/11/2026

- Backend completo: `mvn verify` com BUILD SUCCESS, 1.006 testes, zero falhas/erros; 894 executados com sucesso e 112 testes opt-in ignorados.
- Após reforçar a validação de IDs/campos nulos em lotes, os 11 testes de catálogo e um novo `verify` direcionado também passaram, gerando o pacote final.
- PostgreSQL local 18.4: migração em duplicidade, seed de 48 categorias e preservação dos dados passaram; teste adicional de migração com COMMIT e Hibernate `validate` passou.
- Frontend: 95 testes passaram, TypeScript sem erros, lint sem erros (cinco avisos preexistentes), build de produção local passou.
- Chrome isolado: desktop 1440 px e mobile 390/320 px sem overflow horizontal; menu e filtros móveis funcionais; paginação preserva filtros; categoria → detalhe → adicionar → carrinho → checkout; estado vazio; Mais vendidos ausente sem vendas. ADM móvel: criação de categoria, formulário de classificação e seleção em lote conferidos contra API sintética.
- O fluxo backend de cadastro → classificação → publicação → descoberta → pedido → checkout foi validado com gateway Mercado Pago simulado, mantendo preços, frete e baixa de estoque.
