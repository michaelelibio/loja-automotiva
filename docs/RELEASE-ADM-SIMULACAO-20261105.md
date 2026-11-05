# Release controlada ADM — 05/11/2026

## Escopo e manifesto

Base exata: `a67f880636e86557f9e20f190fddb6b002798a1f`, branch atual
`release/cotacao-assistida-controlada`. O Railway está executando essa base no serviço
`loja-automotiva`, ambiente `production`, raiz `/backend`.

Manifesto exato do **delta**: [19 arquivos](RELEASE-ADM-SIMULACAO-20261105.files.txt).
Os demais arquivos da base continuam necessários. Não publicar somente o delta como
se fosse um projeto independente. Não usar `git add .` nem publicar esta árvore de
trabalho inteira: existem alterações locais de frete fora do escopo.

Incluídos: seis classes Java da operação administrativa já implementada, dois arquivos
de configuração com seus bloqueios preservados, uma migração aditiva, testes backend,
quatro arquivos frontend, dois scripts operacionais e três documentos/manifesto.
Nenhuma funcionalidade de aplicação foi desenvolvida nesta preparação.

Excluídos: alterações em OrderRepository, LegacyOrderCheckout, CommercialFreight*,
CommercialShipping*, ImmediateFreightCalculator, testes de frete provisório,
VerifiedFreeDelivery, ProvisionalFreightPolicy, frontend/archive, next-env.d.ts,
relatórios avulsos, arquivos estranhos da raiz, .env*, *.local*, node_modules,
target e logs locais. O índice Git não foi alterado por esta preparação.

Validação usa uma exportação limpa da base + os 19 arquivos do manifesto, em
`backend/target/adm-release-20261105/source`. Nenhum arquivo provisório excluído entra
nesse build. A junção node_modules é somente uma ferramenta local de validação;
nunca publicá-la ou copiá-la para uma release operacional.

## Schema Railway conferido sem alterações

Em 05/11/2026, consulta por Railway SSH ao serviço Postgres, banco `railway`, PostgreSQL
18.6, em `BEGIN READ ONLY` e `default_transaction_read_only=on`. Somente metadados,
sem consultar registros de pedidos, pagamentos ou clientes.

- V02 = `V20261030_02__commercial_freight.sql`: tabela commercial_freight_tables com
  suas cinco colunas e orders.commercial_shipping compatíveis.
- V03 = `V20261030_03__payment_production_and_supplier_sync.sql`: payments.provider_live_mode
  boolean, payments.provider_verified_at timestamptz e order_fulfillments.supplier_sync
  jsonb presentes, todos nullable, como esperado.
- As nove colunas verificadas coincidem em tipo e nulabilidade.
- `public.admin_aliexpress_test_runs` **ainda não existe**.

Única migração necessária desta release:
`backend/src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql`.
Cria somente a nova tabela, 13 colunas, chave primária order_id e unicidade de real_key.
Não contém UPDATE, DELETE, backfill ou alterações nas tabelas financeiras/históricas.
É compatível com V02/V03; não reaplicar V02/V03, schema.sql ou os wrappers antigos.

O código novo exige a tabela no startup Hibernate validate. Aplicar a migração antes
de publicar o backend novo; o backend anterior continua compatível com a tabela extra.
Não usar ddl-auto=update. A migração não foi executada, nem mesmo para ensaio local,
em respeito à instrução desta preparação. Testes locais usam o schema H2 gerado por JPA;
isso não equivale a um ensaio PostgreSQL de aplicação da migração.

## Bloqueios revisados

Railway consultado somente para leitura: controlled; PAYMENT_CREATION_ENABLED=false;
APP_PAYMENT_CREATION_ENABLED=false; validate nos dois aliases; SQL init never;
ALIEXPRESS_ORDERS_ENABLED=false; contrato false; DSers false; seed false; diagnóstico
Mercado Pago false. Flags de sync/commercial/real-test ausentes usam defaults false
e as restrições de perfil/código. Nenhuma variável remota foi modificada.

Lista administrativa de pedidos de teste ausente/vazia; configuração de destinatário
real de teste ausente. #18 não está na lista. Não foi consultado nem modificado.
Não existe evidência segura preexistente nesta configuração para usá-lo como teste.

Garantias verificadas por revisão e testes:

- `/api/admin/**`, `@PreAuthorize(hasRole('ADMIN'))` e AdminShell restringem o acesso.
- Ações de simulação exigem o ID na lista privada do backend; o navegador não pode
  marcar um pedido ou autorizar sua inclusão.
- Simular pagamento grava somente admin_aliexpress_test_runs. Não chama markPaid,
  PaymentRepository, aprovação Mercado Pago ou ciclo de fulfillment normal.
- AliExpress simulado gera ID SIM-AE-* local. Não chama tokens.accessToken nem client.create.
- O envio normal mantém a exigência de pagamento Mercado Pago de produção verificado.
- A criação real de teste é independente e bloqueada em controlled, por default false,
  por fixture ausente e por verificação homologada ausente. Placeorder e query permission
  continuam false no código. Não existe pagamento automático ao fornecedor.

Publicar inicialmente com a lista vazia é seguro, mas **nenhum botão de simulação
aparecerá** até existir um pedido próprio, identificado e autorizado para teste.
Somente depois dessa identificação, configurar seu ID no backend e publicar/reiniciar
de maneira controlada. Nesta release, não usar #18 nem IDs de clientes reais.
Não criar identificações por edição de status financeiro ou SQL em orders/payments.

## Configuração operacional a manter

Preservar credenciais e referências atuais. Antes de qualquer promoção, pausar
autodeploys Railway/Vercel e registrar os deployments e variáveis anteriores em local
privado. Conferir overrides Spring, argumentos de startup e SPRING_APPLICATION_JSON:
nenhum pode contradizer os gates abaixo. Não imprimir secrets ou listar todas as
variáveis no terminal. A nova variável de lista não é uma flag global de simulação.

Railway: valores seguros abaixo, sem habilitar local-freight/homologation.
O comando **muda configuração**, mas `--skip-deploys` impede disparar deploy pelo CLI;
executá-lo somente pelo operador na janela autorizada. Não foi executado aqui.

```powershell
$projectId = 'f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79'
$service = 'loja-automotiva'
$environment = 'production'
railway.cmd variable set --project $projectId --service $service --environment $environment --skip-deploys `
  'SPRING_PROFILES_ACTIVE=controlled' `
  'PAYMENT_CREATION_ENABLED=false' 'APP_PAYMENT_CREATION_ENABLED=false' `
  'JPA_DDL_AUTO=validate' 'SPRING_JPA_HIBERNATE_DDL_AUTO=validate' 'SPRING_SQL_INIT_MODE=never' `
  'ALIEXPRESS_ORDERS_ENABLED=false' 'ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false' `
  'ALIEXPRESS_ORDER_SYNC_ENABLED=false' 'ALIEXPRESS_ORDER_SYNC_CONTRACT_CONFIRMED=false' `
  'DSERS_SIMULATION_ENABLED=false' 'PRODUCT_SEED_ENABLED=false' `
  'COMMERCIAL_FREIGHT_ENABLED=false' 'MERCADO_PAGO_DIAGNOSTIC_LOGGING=false' `
  'ALIEXPRESS_ADMIN_TEST_REAL_ENABLED=false' 'ALIEXPRESS_ADMIN_TEST_ORDER_IDS=' `
  'ALIEXPRESS_ADMIN_TEST_FIXTURE_ORDER_ID=0' 'ALIEXPRESS_ADMIN_TEST_FIXTURE_JSON='
if ($LASTEXITCODE -ne 0) { throw 'Configuracao falhou; nao publicar' }
```

Manter FRONTEND_URL/CORS_ALLOWED_ORIGINS para os domínios já usados. Não substituir
DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET nem OAuth/secrets por valores de testes.
Não trocar APP_PAYMENT_CREATION_ENABLED para true ao habilitar a lista administrativa.

Depois de existir comprovação e autorização do pedido próprio, o operador pode
substituir a lista vazia pelo ID aprovado. O ID abaixo não é inferido de um pedido
existente, e #18 é recusado porque não há identificação segura preexistente disponível.
Executar somente após revisar essa procedência; depois publicar o mesmo código
aprovado pelo procedimento Railway para efetivar a nova configuração.

```powershell
$testOrderId = Read-Host 'ID do pedido proprio com identificacao de teste comprovada e autorizada'
if ($testOrderId -notmatch '^[1-9][0-9]*$' -or $testOrderId -eq '18') { throw 'ID nao autorizado para este roteiro' }
railway.cmd variable set --project $projectId --service $service --environment $environment --skip-deploys "ALIEXPRESS_ADMIN_TEST_ORDER_IDS=$testOrderId"
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel configurar a lista de teste' }
```

Vercel Production: `NEXT_PUBLIC_API_BASE_URL=https://api.ingarage.com.br`,
`NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false`; se NEXT_PUBLIC_API_URL existir, mesma API.
Root Directory `frontend`, Build Command `npm run build -- --webpack`, como validado.
Secrets de backend jamais entram em NEXT_PUBLIC_*. Manter os identificadores públicos
existentes, inclusive Google. Não herdar URLs de localhost do build local.

## Preparar fonte limpa e revisar seleção (somente operador)

Com autodeploy pausado, a partir da raiz da árvore atual. O manifesto corresponde a
arquivos completos; não inclui nenhuma seleção parcial de frete desta vez.
As verificações seguintes param se o índice não estiver vazio ou a base tiver mudado.
Commit/push são opções manuais do operador, nunca feitos por esta preparação.

```powershell
if ((git rev-parse HEAD) -ne 'a67f880636e86557f9e20f190fddb6b002798a1f') { throw 'Base mudou; revalidar' }
if (git diff --cached --name-only) { throw 'Indice nao vazio; revisar antes de prosseguir' }
$releaseFiles = @(Get-Content -LiteralPath docs/RELEASE-ADM-SIMULACAO-20261105.files.txt)
git switch -c release/adm-simulacao-controlada-20261105
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel criar branch' }
git add -- $releaseFiles
if ($LASTEXITCODE -ne 0) { throw 'Staging falhou' }
$difference = Compare-Object ($releaseFiles | Sort-Object) (@(git diff --cached --name-only) | Sort-Object)
if ($difference) { throw 'Indice diverge do manifesto' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Diff invalido' }
git diff --cached --stat
git diff --cached
# Revisar conteudo completo; somente depois, se autorizado:
git commit -m 'release: prepara simulacao administrativa isolada e controlada'
if ($LASTEXITCODE -ne 0) { throw 'Commit falhou' }
$approvedSha = git rev-parse HEAD
git push -u origin release/adm-simulacao-controlada-20261105
if ($LASTEXITCODE -ne 0) { throw 'Push falhou' }
```

Publicar de checkout limpo desse SHA, nunca da árvore original com frete provisório.
Pode ser outro clone local; não incluir credenciais, logs ou junções node_modules.
Se os autodeploys não puderem ser pausados, não fazer push antes de preparar banco e
ordem de publicação. Nenhum push deve antecipar a migração/backend no Vercel.

## Preflight e migração: comandos para execução manual

Antes: backup Railway concluído e restauração validada em ambiente isolado; conferir
projeto/ambiente e SHA aprovado. Não restaurar sobre produção para ensaiar. Repetir
o preflight imediatamente antes da janela. Não é necessário criar proxy público;
o banco atual usa rede privada e Railway SSH. As duas sessões abaixo usam apenas SQL
fixo do repositório, sem enviar dados pessoais ou segredos como argumentos.

```powershell
# Na raiz do checkout limpo. Este bloco somente consulta metadados.
$projectId = 'f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79'
$sql = Get-Content -LiteralPath backend/ops/admin-simulation-schema-preflight.sql -Raw -Encoding UTF8
$sqlBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($sql))
$remoteCommand = "printf '%s' '" + $sqlBase64 + "' | base64 -d | PGOPTIONS='-c default_transaction_read_only=on' psql -X -w -v ON_ERROR_STOP=1"
railway.cmd ssh --project $projectId --service Postgres --environment production -- $remoteCommand
if ($LASTEXITCODE -ne 0) { throw 'Preflight falhou; nao migrar' }
```

Exigir nove `compatible=t` para V02/V03. A tabela nova pode estar ausente antes da
migração. Se já existir, revisar suas 13 colunas/PK/UNIQUE em vez de presumir que
CREATE IF NOT EXISTS corrige estrutura divergente. Não executar V02 ou V03 novamente.

```powershell
# ESTE BLOCO ALTERA O SCHEMA. Somente apos backup, revisao e autorizacao da janela.
if ((Read-Host 'Digite MIGRAR ADM SIMULACAO PRODUCTION para confirmar o alvo') -cne 'MIGRAR ADM SIMULACAO PRODUCTION') { throw 'Cancelado' }
$wrapper = Get-Content -LiteralPath backend/ops/admin-simulation-migration.sql -Raw -Encoding UTF8
$ddl = Get-Content -LiteralPath backend/src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql -Raw -Encoding UTF8
$include = '\ir ../src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql'
if (-not $wrapper.Contains($include)) { throw 'Wrapper inesperado; revisar' }
$migrationSql = $wrapper.Replace($include,$ddl)
$migrationBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($migrationSql))
$migrationCommand = "printf '%s' '" + $migrationBase64 + "' | base64 -d | psql -X -w -v ON_ERROR_STOP=1"
railway.cmd ssh --project $projectId --service Postgres --environment production -- $migrationCommand
if ($LASTEXITCODE -ne 0) { throw 'Migracao falhou; nao promover backend; investigar' }
# Repetir o bloco READ ONLY e conferir as 13 colunas, PK(order_id), UNIQUE(real_key).
```

O wrapper aplica apenas V20261104_01 em transação, com timeout de lock/statement e
ON_ERROR_STOP. Falhas antes de COMMIT abortam a transação ao fechar a conexão.
Não existe rollback destrutivo de schema nesta release.

## Publicar backend Railway primeiro

Manter raiz remota `/backend`. Este comando envia a raiz do checkout limpo inteiro,
permitindo ao serviço usar sua raiz já configurada. Não usar `--path-as-root` neste
layout e não usar `--no-gitignore`. Não executar `railway up` da árvore suja original.

```powershell
# No checkout limpo do SHA aprovado; confirmar projeto/servico antes.
if (git status --porcelain) { throw 'Checkout nao esta limpo' }
$approvedSha = Read-Host 'SHA completo aprovado da release'
if ($approvedSha -notmatch '^[0-9a-f]{40}$' -or (git rev-parse HEAD) -ne $approvedSha) { throw 'SHA incorreto' }
railway.cmd up . --project $projectId --service loja-automotiva --environment production
if ($LASTEXITCODE -ne 0) { throw 'Deploy backend falhou; nao publicar frontend' }
railway.cmd deployment list --project $projectId --service loja-automotiva --environment production --limit 3
railway.cmd logs --project $projectId --service loja-automotiva --environment production --lines 80
```

Revisar logs localmente sem compartilhar dados sensíveis. Exigir sucesso de startup,
perfil controlled, Hibernate validate, SQL init never e nenhuma falha de schema.
Conferir GET `/api/products` 200, rota administrativa sem token 401 e com cliente 403.
Com ADMIN, GET `/api/admin/orders/{id}/aliexpress-tests` de pedido não marcado deve
mostrar `testOrder=false` e `canCreateReal=false`; não executar simulação em pedido
de cliente. Não chamar `/aliexpress/send`, `/sync` ou `/create-real` na publicação.

Só habilitar um ID depois de confirmar sua procedência e autorização de teste.
A lista fica vazia na primeira publicação; nenhuma escrita de teste faz parte deste
procedimento de deploy. Os testes de HTTP 409 de pagamento e zero chamadas reais já
estão na suíte local; qualquer ensaio remoto posterior deve usar pedido autorizado.

## Publicar frontend Vercel depois do backend validado

No painel Vercel, confirmar o projeto inGarage já existente, root frontend, variáveis
Production e Build Command acima. Não criar projeto novo por engano. Registrar a URL
imutável do deployment anterior para rollback. Com CLI autenticado e instalado:

```powershell
# Na RAIZ do repositorio limpo; projeto remoto usa Root Directory frontend.
vercel link
# Confirmar explicitamente equipe/projeto existente; nao aceitar criar outro.
vercel deploy --prod --skip-domain
if ($LASTEXITCODE -ne 0) { throw 'Build/deploy Vercel falhou; manter dominio anterior' }
$newFrontendUrl = Read-Host 'URL exata do deployment staged de Production retornada pelo Vercel'
vercel inspect $newFrontendUrl
# Abrir a URL, conferir login/ADM/API/CORS; somente depois promover:
vercel promote $newFrontendUrl
if ($LASTEXITCODE -ne 0) { throw 'Promocao falhou; conferir dominio antes de continuar' }
```

`next build` local **não** gera um artefato Vercel prebuilt. Não usar `--prebuilt`
com somente `.next`; este procedimento deixa o Vercel reconstruir o SHA/fonte
selecionado com as variáveis Production e não aponta o domínio até a promoção.
Conservar Root Directory `frontend` e executar a CLI na raiz do repositório,
conforme o [procedimento oficial para monorepos](https://vercel.com/docs/monorepos).
Não executar a CLI dentro de frontend com a mesma raiz remota, duplicando a seleção.
O vínculo .vercel gerado pela CLI é local e não faz parte do manifesto.

Após promoção: abrir `/admin/pedidos/{id}` com ADMIN e pedido próprio autorizado;
usuário comum não vê o ADM e chamadas diretas falham. Simular pagamento, executar
AliExpress simulado, recarregar e conferir datas/responsáveis/ID SIM-AE-*. Conferir
que pagamento financeiro e status real mantiveram seus valores anteriores, sem novo
registro Mercado Pago, fulfillment de produção ou tráfego placeorder. A ação real
permanece indisponível. Não usar #18 neste roteiro.

## Rollback

Primeiro conter tráfego se houver falha de segurança; manter gates false. Não usar
remoção de tabela/colunas, deleção de pedidos, atualização financeira ou restauração
de dump sobre produção como primeiro recurso. Conservar admin_aliexpress_test_runs
e suas chaves de auditoria/idempotência, mesmo que não tenham sido utilizadas.

Frontend (CLI no projeto correto):

```powershell
$previousFrontendUrl = Read-Host 'URL imutavel do deployment Production anterior registrado'
vercel rollback $previousFrontendUrl
if ($LASTEXITCODE -ne 0) { throw 'Rollback Vercel falhou; conferir painel e dominio' }
vercel rollback status
```

Railway: a CLI instalada não oferece `railway rollback`. No painel, serviço
loja-automotiva → Deployments → deployment anterior → menu → Rollback.
Referência observada nesta preparação: `f0acfa4d-cee8-4f76-8729-ffdcb813f755`,
SHA `a67f880636e86557f9e20f190fddb6b002798a1f`; confirmar novamente antes de usar.
O rollback restaura imagem **e variáveis**: reconferir controlled, pagamento false,
AliExpress false e validate/never antes de reabrir tráfego. Não usar `redeploy` como
sinônimo de rollback: ele não seleciona automaticamente a imagem anterior.
Se a imagem anterior não estiver retida/disponível, publicar de checkout limpo do
SHA anterior conhecido e revisado pelo mesmo procedimento Railway, nunca uma versão
sem os gates financeiros. Nenhum rollback SQL é necessário para o aplicativo antigo.

## Referências oficiais e evidências

Comandos conferidos no help da CLI Railway instalada e na documentação oficial:
[deploy CLI Railway](https://docs.railway.com/cli/deploying),
[rollback Railway](https://docs.railway.com/deployments/deployment-actions),
[staged Production Vercel](https://vercel.com/docs/cli/deploying-from-cli),
[rollback Vercel](https://vercel.com/docs/cli/rollback).

Logs locais, fora do manifesto: `backend/target/adm-release-20261105/`.
Nenhum commit, push, migração, mudança de variável, compra, pagamento ou deploy foi
executado nesta preparação. As únicas operações remotas foram consultas de configuração,
identificação de deployment e leitura de metadados em transação READ ONLY.

## Validação final da seleção exata

- Backend: `mvn verify` completo com BUILD SUCCESS. 801 casos reportados: **745
  executados/aprovados**, 56 ignorados, zero falhas e zero erros. Ignorados são probes
  externos e suítes opcionais PostgreSQL; nenhuma migração PostgreSQL foi executada.
- JAR executável de produção gerado pelo spring-boot:repackage em
  `backend/target/adm-release-20261105/source/backend/target/garage-api-0.0.1-SNAPSHOT.jar`.
- Frontend: **67 testes aprovados**, zero falhas. O teste provisório de frete fora do
  manifesto explica a diferença em relação aos 68 testes da árvore de trabalho.
- Lint completo: zero erros, cinco avisos preexistentes em arquivos da base, sem avisos
  novos nos botões. Build Production Next.js 16.3.0 com Webpack concluído, TypeScript
  aprovado e 35 páginas estáticas geradas, além das rotas dinâmicas.
- Webpack foi usado porque a cópia isolada compartilha node_modules por junção fora
  da raiz, incompatível com o confinamento Turbopack. Nenhuma dependência ou arquivo
  de configuração de aplicação foi modificado para contornar isso. O comando Vercel
  documentado usa o mesmo builder validado.
- Testes/build Java locais no JDK 25 com target Java 17; Railway atualmente usa Java 21.
  A validação não alega execução local no JDK 21 nem homologação de provedores reais.
- Revisão confirmou que a migração está fora de SQL init automático e controlled
  permanece validate/never. Somente a nova tabela é necessária antes do deploy.

SHA-256 dos artefatos locais conferidos (novos builds podem gerar outro hash do JAR):

```text
V20261104_01__admin_aliexpress_test_runs.sql
1889DD6F0D3B7ABF158A71B72B43F3D1750730A9C4111446761F4C86D22F8DB8
garage-api-0.0.1-SNAPSHOT.jar
52593862AC18FA0A514EDD238719BF717C82D9AB731210A616C8EE5BFAC62783
```

Comandos reproduzíveis no checkout limpo (não incluem migração/deploy):

```powershell
Push-Location backend
.\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { throw 'Suite/build backend falhou' }
Pop-Location
Push-Location frontend
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Instalacao frontend falhou' }
npm.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Suite frontend falhou' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw 'Lint falhou' }
$env:NEXT_PUBLIC_API_BASE_URL='https://api.ingarage.com.br'
$env:NEXT_PUBLIC_PAYMENT_CREATION_ENABLED='false'
npm.cmd run build -- --webpack
if ($LASTEXITCODE -ne 0) { throw 'Build frontend falhou' }
Pop-Location
```

Neste ambiente, o wrapper Maven exigiu usar diretamente a distribuição já instalada
em `.m2/wrapper/dists` com `-Dmaven.repo.local=C:/Users/Michael/.m2/repository` por
restrições do sandbox. Isso não altera pom.xml nem o comando de build do Railway.
Não carregar .env.local.ps1 de produção para executar testes. Manter probes live e
suítes opcionais PostgreSQL desativados até haver autorização própria para executá-los.
