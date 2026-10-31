# Release controlada do checkout — 2026-10-30

Base: branch `release/cotacao-assistida-controlada`, commit `437e891`.
Manifesto: `RELEASE-CHECKOUT-CONTROLLED-20261030.files.txt` (65 caminhos explícitos; delta da release, não o repositório inteiro).

## Escopo e seleção

Checkout `CATALOG_FREE`: produtos AliExpress ativos selecionados no ADM, entrega R$ 0,00, total calculado no backend, validação de endereço/variante/estoque e chave idempotente. Não lê `VERIFIED_FREE_DELIVERY_EVIDENCE` e não exige cotação, tabela estadual ou evidência de entrega. Compatibilidade com snapshots históricos `VERIFIED_FREE` permanece.

Inclui persistência de `live_mode` obtido na consulta autenticada Mercado Pago, bloqueio de encaminhamento sem pagamento de produção verificado, todos os IDs AliExpress e sincronização somente de leitura por ID. Pagamento ao fornecedor permanece manual. Não existe botão que transforme um registro visual em confirmação de pagamento ao fornecedor.

Frete comercial, cotação assistida e diagnósticos de frete ficam inacessíveis em produção: rota de administração retorna 404, calculadores e ações removidos das páginas, controllers disponíveis apenas em `local-freight & !controlled & !production & !prod`. Dados e migrações históricos permanecem.

Três arquivos têm seleção parcial no índice, diferente do diretório de trabalho:

- `CommercialFreightController.java`: somente restrição de perfil, sem endpoint provisório.
- `LegacyOrderCheckout.java`: checkout histórico isolado, usando `commercial.evaluate` existente na base; sem depender do novo `forOrder` provisório.
- `frontend/tests/assisted-quote.test.cjs`: teste de 404 incluído; teste da política provisória excluído.

Não executar novamente `git add` nesses três arquivos: isso incluiria alterações fora do escopo. Revisar sempre `git diff --cached`. Permanecem fora: política provisória e testes associados, `OrderRepository` (somente exposição comercial), calculador antigo modificado, `next-env.d.ts`, `VerifiedFreeDelivery.java` sem referências, `frontend/archive/` inteiro, relatórios antigos em `docs/`, evidências/relatórios avulsos do backend e os dois arquivos estranhos não rastreados da raiz. Nenhum arquivo excluído foi apagado.

`.env.example` contém valores vazios e bloqueios false; nenhum token ou credencial real deve ser versionado. `.gitignore` da raiz já protege `.env`, `.env.*` e `*.local`; ajustes de backend/frontend protegem artefatos de validação. Arquivos já rastreados não passam a ser protegidos por ignore: revisar o índice antes do commit.

## Configuração obrigatória Railway

Preservar as conexões e segredos existentes apenas no gerenciador de variáveis. Confirmar configuração efetiva, inclusive overrides Spring de maior prioridade:

```text
SPRING_PROFILES_ACTIVE=controlled
PAYMENT_CREATION_ENABLED=false
ALIEXPRESS_ORDERS_ENABLED=false
ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false
ALIEXPRESS_ORDER_SYNC_ENABLED=false
ALIEXPRESS_ORDER_SYNC_CONTRACT_CONFIRMED=false
DSERS_SIMULATION_ENABLED=false
PRODUCT_SEED_ENABLED=false
COMMERCIAL_FREIGHT_ENABLED=false
MERCADO_PAGO_DIAGNOSTIC_LOGGING=false
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_SQL_INIT_MODE=never
```

Não ativar `local-freight`; remover overrides contraditórios como `APP_PAYMENT_CREATION_ENABLED` ou `ALIEXPRESS_ORDERS_SYNC_ENABLED`. Confirmar `FRONTEND_URL` e `CORS_ALLOWED_ORIGINS` para o domínio Vercel. Não publicar segredos em `NEXT_PUBLIC_*`. Vercel precisa de `NEXT_PUBLIC_API_BASE_URL` apontando ao backend Railway antes do build.

## Banco: exigências, sem acesso à produção

Não é possível afirmar quais migrações estão pendentes sem histórico/schema de produção. Nenhuma conexão ao banco foi realizada nesta preparação. O projeto usa scripts Spring SQL e validação Hibernate, não migração Flyway automática. Com `controlled`, os scripts não são executados na inicialização.

Inventariar posteriormente, com acesso autorizado e backup, quais destes scripts já estão aplicados, respeitando a ordem e as dependências:

1. `schema.sql` (base; não reaplicar indiscriminadamente).
2. `V20260820_01__order_item_supplier_snapshot.sql`
3. `V20260820_02__order_shipping_provider_snapshot.sql`
4. `V20260820_03__order_fulfillment.sql`
5. `V20260821_01__product_media.sql`
6. `V20261014_01__aliexpress_import.sql`
7. `V20261015_01__aliexpress_manual_orders.sql`
8. `V20261030_01__assisted_quote.sql`
9. `V20261030_02__commercial_freight.sql`
10. `V20261030_03__payment_production_and_supplier_sync.sql`

Os itens 1–9 já pertencem à base Git; não precisam ser artificialmente adicionados ao delta. As estruturas históricas continuam mapeadas, mesmo com interfaces desativadas. V20261030_03 adiciona `payments.provider_live_mode`, `payments.provider_verified_at` e `order_fulfillments.supplier_sync`; NULL é desconhecido, nunca prova de produção. Não preencher pagamentos antigos como live sem evidência do provedor. Publicação fica bloqueada até verificar/aplicar somente o que faltar em uma janela autorizada; não usar `ddl-auto=update` como substituto.

## Commit e publicação (somente pelo operador)

Antes do push, controlar as integrações de publicação automática para impedir Vercel de antecipar Railway. Confirmar a branch configurada em cada serviço; não presumir que o push desta branch publica produção.

```powershell
git branch --show-current
git diff --cached --check
git diff --cached --name-status
git diff --cached
git commit -m "release: prepara checkout limpo com operacao controlada"
git rev-parse HEAD
git push origin release/cotacao-assistida-controlada
```

1. Registrar o SHA final e validar o inventário de migrações; backup e aplicação das pendentes são etapas posteriores, autorizadas separadamente.
2. Railway: selecionar o SHA aprovado, raiz `backend`, manter `controlled` e todos os bloqueios. Publicar primeiro o backend, depois de o schema estar compatível. Conferir inicialização, validação Hibernate, autenticação e logs sem dados sensíveis.
3. Validar a nova elegibilidade autenticada e criação de pedido interno com conta/produto de teste autorizado, sem chamar pagamento nem `/aliexpress/send`. Confirmar preço do backend, entrega zero e repetição da mesma requestKey retornando o mesmo pedido. Verificar rejeição de total adulterado e APIs financeiras bloqueadas. Não ativar simulação no ambiente de produção.
4. Vercel: raiz `frontend`, mesma branch/SHA, variáveis públicas corretas; publicar somente após Railway validado. Validar catálogo, carrinho, checkout, pedido interno e ADM. `/admin/fretes` deve retornar 404; sem calculadores, revisão nacional ou cotação assistida.
5. Em falha, interromper a promoção e reverter deployments ao par anterior. Não desfazer automaticamente migrações aditivas nem apagar pedidos. O frontend antigo pode ser incompatível com o backend novo; planejar janela controlada entre as duas publicações.

## Bloqueios de homologação e riscos

- Mercado Pago: validar consulta autenticada, associação do pagamento, valor/moeda/referência, assinatura de notificação e persistência de `live_mode` no ambiente homologado. Testes locais não provam uma cobrança real. Encaminhamento exige PAID, método Mercado Pago, ID, paidAt, `live_mode=true` e consulta registrada.
- AliExpress: `aliexpress.trade.buy.placeorder` e `aliexpress.ds.trade.order.get` precisam de permissão efetivamente concedida à aplicação vinculada, contrato/transporte/resposta e comportamento de timeout/reconciliação homologados. Autorização documentada não prova concessão nem execução. Os dois componentes de permissão retornam false no código: mudar variáveis não os libera. Não executar placeorder para descobrir permissão. Criação e consulta reais continuam bloqueadas nesta release.
- Payload de criação, omissão de logística opcional, identificador de correlação e mapeamento de status (inclusive `RISK_CONTROL`) ainda dependem de prova do contrato real. Nenhuma compra ou pagamento automático foi usado como teste. Estado desconhecido/timeout não autoriza reenvio.
- A página institucional de entrega ainda contém critério de prazo por definir: decisão editorial/comercial pendente para uma abertura pública irrestrita.
- Os 805 testes backend/65 frontend citados são evidência da árvore anterior, que continha alterações provisórias excluídas. A seleção exata do índice precisa de validação própria; não atribuir automaticamente esses números à release.

Esta preparação não executa commit, push, migração, deploy, cobrança ou compra.

## Validação do conteúdo selecionado

Exportação isolada do índice, sem arquivos não selecionados: 64 testes frontend passaram; lint sem erros, com 5 avisos; build de produção com `npm.cmd run build -- --webpack` e TypeScript passaram. O teste provisório excluído explica a diferença em relação aos 65 anteriores. O build padrão Turbopack não aceita a junção local de node_modules apontando para fora da cópia; o build Webpack foi usado nesta validação, sem alterar package.json. A execução precisou de acesso público ao Google Fonts para baixar as fontes já usadas na base. Nenhuma dependência ou funcionalidade foi adicionada.

Backend: `mvn test` na exportação isolada terminou em BUILD SUCCESS: 789 casos, 733 executados/aprovados, 56 ignorados, 0 falhas e 0 erros. Os testes opcionais PostgreSQL não foram ativados nesta rodada; portanto, ela não comprova aplicação de scripts em PostgreSQL nem substitui a homologação externa. Os números anteriores de 805/65 não devem ser usados como contagem desta seleção. Compilação de todas as fontes e testes passou sem as dependências provisórias excluídas.

Conferência final: manifesto corresponde exatamente aos 65 caminhos do índice; `git diff --cached --check` sem erros. Revisão de `.env.example`, ignores e busca de padrões de credenciais no conteúdo selecionado sem ocorrência de segredo real identificado. Arquivos de trabalho excluídos e trechos provisórios permanecem locais, sem staging.
