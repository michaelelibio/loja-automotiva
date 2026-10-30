# Frete nacional — revisão em lote e publicação controlada

## Entrega e limites

Em `/admin/fretes`, a seção **Tabela nacional: preparação e revisão em massa** prepara 27 UFs × 3 portes (81 linhas) para cada escopo categoria/fornecedor/origem informado e conferido. Não infere fornecedor, categoria, porte do SKU, origem, logística, prazo, custos, tributos ou escala monetária. Não usa R$ 24 ambíguos. Nenhuma tarifa foi cadastrada no Railway.

As linhas planejadas são persistidas no JSONB da mesma tabela `commercial_freight_tables`, em `document.reviewRows`. Não há sistema de pedidos paralelo nem nova migração além de V20261030_02. Documentos antigos sem reviewRows continuam legíveis. Rascunhos nacionais aceitam custos e política ainda desconhecidos. `document.rates` contém exclusivamente as linhas aprovadas e estruturalmente completas; o backend ignora a lista rates submetida ao endpoint bulk e a reconstrói a partir das aprovações.

O status inicial é rascunho/inativo, mesmo com aprovação de algumas linhas. Publicar é uma ação administrativa separada, depende de `COMMERCIAL_FREIGHT_ENABLED=true`, política completa, perfis de SKU válidos e evidências vigentes. A rentabilidade é calculada sobre o carrinho real, não presumida a partir de uma tarifa isolada. Margem insuficiente, impostos desconhecidos, validade expirada ou cobertura ausente mantêm o fallback de cotação assistida.

## Operação no ADM

1. Abrir `/admin/fretes`, autenticado como ADMIN.
2. Preencher no formulário existente a reserva, taxas máximas comprovadas dos meios de pagamento/parcelamentos, limites de margem e lucro e perfis reais de SKU. Para cada SKU: produto/variante do catálogo, tamanho, fornecedor e origem conferidos, custos e evidência. Pode manter campos desconhecidos vazios durante o planejamento.
3. Na revisão nacional, informar categoria exata, fornecedor/loja e origem conferidos. Clicar **Preparar 27 UFs × 3 portes sem preços**. Repetir para outro escopo quando necessário. A ação adiciona somente combinações ausentes, sem sobrescrever preços. Também é possível carregar uma versão existente e usar **Completar cobertura nacional dos escopos carregados**.
4. Usar **Atualizar política, reserva e perfis do formulário abaixo** após editar o formulário. Essa ação retira todas as aprovações para nova revisão.
5. Filtrar UF e/ou porte e aplicar um preço comercial às linhas filtradas. Esse comando altera SOMENTE o frete do cliente e retira aprovação; não inventa nem altera o custo AliExpress.
6. Em **Revisar custos e evidência**, registrar custo do fornecedor, impostos, logística, prazo, domínio fiscal/quantidade, embalagem e evidência específica daquela cobertura. Toda edição retira a aprovação da linha. O preço é fixo por UF/porte/volume; múltiplos volumes seguem a capacidade e a consolidação comprovadas.
7. Marcar as linhas individualmente ou usar aprovação em lote para as linhas filtradas. Essa marcação solicita validação: salvar rejeita aprovação incompleta, ambígua, expirada, duplicada ou sem SKU AliExpress na categoria/porte/fornecedor/origem. Não equivale a uma publicação.
8. **Salvar revisão nacional como rascunho** persiste uma versão imutável. Linhas não aprovadas continuam com seus campos nulos e não entram no cálculo. Se uma aprovação inválida impedir o salvamento, retirar essa aprovação permite guardar o planejamento.
9. Quando autorizado, publicar a nova versão pela seção **Versões**. A publicação substitui integralmente a versão comercial anterior, não é um patch de UFs. Carregar a tabela completa anterior antes de atualizar um subconjunto; não publicar somente um fragmento esperando que as outras UFs sejam herdadas.

## Importação/atualização em lote

Usar **Exportar revisão JSON** como modelo. O arquivo contém a política, perfis e `reviewRows: [{rate: {...}, approved: false}]`. As 81 linhas geradas já vêm com chaves únicas e escopos separados. Campos monetários desconhecidos são `null`, não zero. Zero só deve representar custo/preço efetivamente confirmado.

Editar o arquivo para atualizar várias UFs, importar e conferir a matriz. A importação limpa TODAS as aprovações e ignora rates; nunca publica. Limites: 4 MB no cliente, 2000 linhas e 500 perfis por versão. UF/chave/escopo duplicados são recusados; o backend repete as validações, independentemente da tela. Não guardar dados de destinatários, tokens ou documentos pessoais nas referências/arquivos.

Endpoint novo ADMIN: `POST /api/admin/shipping-tables/bulk`. Reutilizados: GET/POST `/api/admin/shipping-tables`, POST `/{version}/publish`, POST `/{version}/retire`; checkout continua em POST `/api/shipping/quote` e `/api/orders`. Importar ou abrir o ADM não cria pedidos nem pagamentos.

## Auditoria do backup autorizado

O backup local autorizado, anterior a V20261030_01, foi restaurado em **banco novo** `ingarage_restore_d3f58cadb66a`, no cluster de testes `127.0.0.1:52639`, separado do PostgreSQL habitual em 5432 e de produção. O arquivo original foi verificado por SHA-256 antes/depois e permaneceu intacto. Não foi copiado para o repositório.

O script `ops/verify-restored-freight-migrations.sql` aplicou V20261030_01 e V20261030_02 em ordem, repetiu ambas e comparou fingerprints de todas as 15 tabelas existentes. Resultado: `MIGRATIONS_VERIFIED_NO_EXISTING_RECORDS_CHANGED`. Não houve backfill, preço semeado ou modificação de registros históricos. A auditoria compara os campos antigos; verifica separadamente que os novos snapshots/request_key são nulos nos pedidos históricos. Logs de restauração ficam somente em target ignorado pelo Git, sem divulgação de conteúdo.

A cópia com dados reais foi usada SOMENTE para SQL, sem inicializar aplicação, fazer login, usar OAuth ou executar testes funcionais. Os testes HTTP/funcionais usam banco diferente (`ingarage_homologation`) e dados sintéticos, sem carregar pessoas reais da cópia. Antes de qualquer futuro teste funcional sobre uma cópia, anonimizar dados pessoais e remover credenciais/tokens; não conectar uma aplicação diretamente a este banco restaurado.

## Publicação futura — comandos manuais, não executados

1. Revisar diff e arquivos da entrega na branch `release/cotacao-assistida-controlada`. Não incluir .env, backups, target, `.next`, nem o arquivo estranho preexistente na raiz. Nenhum commit/push/deploy foi realizado nesta execução.
2. Manter no Railway: `SPRING_PROFILES_ACTIVE=controlled`, `PAYMENT_CREATION_ENABLED=false`, `COMMERCIAL_FREIGHT_ENABLED=false`, `ALIEXPRESS_ORDERS_ENABLED=false`, `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false`. O perfil controlled mantém SQL init never e Hibernate validate, além de forçar pagamento e pedidos AliExpress desabilitados. Não trocar por update/create.
3. Manter na Vercel `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false` e a URL pública correta do backend; esta variável é incorporada no build. Não alterar credenciais existentes.
4. Antes de publicar o backend, obter backup novo do banco alvo e ensaiar a restauração. O exemplo usa serviço PostgreSQL configurado localmente via pg_service/pgpass, sem segredos nos comandos. Não usar o backup histórico como rollback de dados novos.

```powershell
# MANUAL: requer serviço PostgreSQL ingarage-production já configurado pelo operador.
$pgBin = 'C:/Program Files/PostgreSQL/18/bin'
$backupFile = Join-Path $env:USERPROFILE ('inGarage-private-backups/ingarage-before-national-' + (Get-Date -Format yyyyMMdd-HHmmss) + '.dump')
& "$pgBin/pg_dump.exe" -d 'service=ingarage-production' --format=custom --file=$backupFile
if ($LASTEXITCODE -ne 0) { throw 'Backup falhou; interromper publicação' }
# Após revisão/autorização, aplicar scripts aditivos em ordem e em transação única.
& "$pgBin/psql.exe" -X -d 'service=ingarage-production' -v ON_ERROR_STOP=1 --single-transaction -c "SET lock_timeout='5s'; SET statement_timeout='30s'; SET search_path=public" -f backend/src/main/resources/db/migration/V20261030_01__assisted_quote.sql -f backend/src/main/resources/db/migration/V20261030_02__commercial_freight.sql
if ($LASTEXITCODE -ne 0) { throw 'Migração falhou; não publicar backend' }
```

5. Publicar backend Railway primeiro e verificar startup validate/saúde/autorização administrativa; depois frontend Vercel. Ambos precisam de atualização para esta funcionalidade.
6. Com a flag comercial ainda false, cadastrar/reabrir rascunho nacional, exportar/importar e confirmar ausência de preços fictícios e fallback de cotação assistida. Verificar HTTP 409 em novas tentativas de pagamento, sem URLs de cobrança. Usar apenas contas e dados de teste.
7. Revisar custos reais, embalagem, tributos e política de rentabilidade. Somente após aprovação comercial explícita, habilitar `COMMERCIAL_FREIGHT_ENABLED=true` e publicar a versão aprovada; isso NÃO libera Mercado Pago. Testar CEPs de cada UF coberta e combinações de volumes com contas de teste. Testes que chamam o endpoint bloqueado de pagamento não devem ter métodos automáticos alternativos habilitados.
8. Manter pagamentos bloqueados até a homologação externa do Mercado Pago e tratamento pós-pagamento de custos/reembolso. Não habilitar encaminhamento automático ao fornecedor.

## Rollback

Retirar de uso a última versão no ADM ou colocar `COMMERCIAL_FREIGHT_ENABLED=false`: mantém fallback assistido. Retirar uma versão não reativa uma versão antiga silenciosamente. Se necessário, republicar conteúdo anterior como NOVA versão, após revisão. Para falha de aplicação, reverter Railway/Vercel às versões anteriores mantendo controlled e pagamentos false. Manter as colunas/tabelas aditivas; não executar DROP nem restaurar backup por cima de pedidos novos. Pedidos com aceite antigo preservam seus snapshots e exigem revalidação; não mudar total unilateralmente.

## Resultado final dos testes — 30/10/2026

- Backend final: 152 testes, zero falhas/erros/ignorados, BUILD SUCCESS e JAR empacotado. Inclui 40 testes comerciais em H2 e 42 em PostgreSQL sintético; cobre 81 linhas sem preço, aprovação parcial, importação, duplicidades, evidência ambígua, RBAC, fallback, preços/margens e HTTP 409 de pagamentos, além das regressões de pedidos e webhook.
- Frontend: 59 testes aprovados; lint zero erros e quatro avisos existentes; build Next.js/TypeScript aprovado.
- Migração em cópia do backup: duas aplicações em ordem, registros das 15 tabelas existentes preservados; arquivo original intacto.
- Cluster local de testes em 52639 encerrado ao concluir; dados preservados, sem afetar o PostgreSQL em 5432. Nenhum teste funcional usou dados reais da cópia.
- Perfil controlled idêntico ao HEAD. Busca por padrões de credenciais nos arquivos alterados sem ocorrências. Nenhuma credencial existente foi alterada.
- Logs locais ignorados pelo Git: target/homologation-final/national-backend-tests-final.log, national-frontend-{tests,lint,build}.log e national-restore-migrations.log.

Status: implementação pronta para revisão/publicação controlada de rascunhos; tarifas reais continuam desabilitadas até custos comprovados, política completa e aprovação comercial. Pagamentos reais continuam bloqueados até homologação externa.