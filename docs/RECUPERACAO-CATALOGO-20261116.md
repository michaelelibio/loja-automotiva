# Recuperação do catálogo em produção — 16/11/2026

## Resultado

Recuperação concluída. Migração completa aplicada no PostgreSQL de produção, seguida de reinício do mesmo commit publicado. Railway Active/SUCCESS; Spring Boot, JPA, PostgreSQL e endpoints verificados. Hibernate permanece em `validate` e nenhuma flag foi alterada.

## Causa confirmada

O deployment `cc35982e-209b-479c-9186-dcad2e4ce404`, commit `4a1f2b973fa272cd09a0e8356493f6df215b8d8d` da `main`, estava CRASHED com `missing table [catalog_categories]`.

O backend usa o PostgreSQL 18.6 do serviço Postgres do mesmo projeto Railway: banco `railway`, schema `public`, usuário `postgres`. Não foi encontrado override apontando para outro datasource, nem tabelas de catálogo em outro schema.

O perfil `controlled` mantém `spring.sql.init.mode=never` e `spring.jpa.hibernate.ddl-auto=validate`. O projeto utiliza migrações SQL manuais e não possui Flyway/Liquibase nem tabela de histórico automático. Incluir o SQL em `src/main/resources/db/migration` ou na lista de inicialização local não o executa nesse perfil. O código do catálogo foi publicado antes da aplicação manual documentada: faltavam as quatro tabelas e todas as dez colunas novas em `products`.

## Backup e revisão anteriores à alteração

- Backup lógico completo atual, obtido por `pg_dump` 18.6 através do SSH Railway em transação somente leitura e snapshot consistente.
- Arquivo criptografado com Windows DPAPI CurrentUser e diretório com ACL restrita, mantido fora do versionamento em `.railway-schema-audit.local/catalog-backup-1794800122950_d5d97c/production.custom.dpapi`.
- O arquivo salvo foi descriptografado e restaurado em transação única em PostgreSQL local isolado. Contagens, fingerprints de todas as linhas, colunas, chaves, checks, índices e sequências conferiram com a origem. O cluster restaurado foi encerrado e removido após a validação.
- SHA-256 do arquivo criptografado: `58a3f7d416100a738cb708c55b975c063bca0feda833467ba79f473ed7125cab`.
- Limite do backup: depende do mesmo perfil Windows para descriptografia; é backup lógico deste banco, sem credenciais globais dos papéis PostgreSQL. A restauração isolada preservou os objetos sem reaplicar proprietários/ACL de produção.
- SQL revisado: `backend/src/main/resources/db/migration/V20261116_01__detailing_catalog.sql`.
- SHA-256 do SQL: `76d407cbac5404f8cc0a9115c9436bcbc93d1ede4f1dfe661b684e3578cacdb4`, igual ao relatório original e ao arquivo publicado.
- As assertions estruturais adicionais de `backend/ops/catalog-migration-postcheck.sql` foram testadas localmente com a migração, terminando em ROLLBACK.

## Transação aplicada

COMMIT confirmado em **16/11/2026 às 00:41:35, America/Sao_Paulo** (`2026-11-16T03:41:35Z`).

A transação revisada está arquivada localmente em `.railway-schema-audit.local/catalog-reviewed-transaction.sql`, SHA-256 `6d537c01d874b32e5c8f56ef0e43370620acbb333f7af2b8d7cc72f88a6566ec`.

Sequência executada:

1. `BEGIN`, search path `public`, timezone UTC, `lock_timeout=5s`, `statement_timeout=45s` e advisory lock da migração.
2. Bloqueio das 22 tabelas existentes durante a operação; confirmação do banco/schema e da ausência das estruturas novas.
3. Comparação de todas as linhas existentes com o backup restaurado, por contagem e fingerprint; registro das sequências existentes.
4. Execução integral do SQL original, sem substituir ou recortar a migração.
5. Verificações antes do COMMIT: colunas/tipos/nullability, quatro tabelas, seis FKs validadas, chaves, índices, hierarquia, 8 raízes e 40 subcategorias; metadados históricos vazios e coleções novas vazias; todas as linhas e sequências anteriores preservadas.
6. `COMMIT` após sucesso de todas as assertions. Verificação adicional em transação somente leitura após o COMMIT.

Não foi criada apenas a tabela isolada. Não houve `Hibernate update`, recriação de tabelas existentes, reclassificação de produtos, restauração de produção, cobrança ou criação de pedido no fornecedor.

| Dados preservados | Antes | Após migração e reinício |
| --- | ---: | ---: |
| Pedidos | 23 | 23 |
| Pagamentos | 17 | 17 |
| Fulfillments | 11 | 11 |
| Produtos | 2 | 2 |
| Itens dos pedidos | 23 | 23 |

As demais tabelas existentes também conservaram todas as linhas. Comparação por fingerprint confirmou a preservação do conteúdo, além das contagens; para produtos, a comparação considera os campos anteriores à migração.

## Reinício e validação

- Redeploy do mesmo commit `4a1f2b973fa272cd09a0e8356493f6df215b8d8d`, sem puxar código novo ou alterar variáveis.
- Novo deployment: `3cdca9f9-74b9-48c5-a9df-533371f5d63f`, Active/SUCCESS.
- Antes do reinício: zero pedidos AliExpress pagos pendentes elegíveis após o corte de ativação.
- Flags e JSON de configuração preservados. Corte mantido em `2026-11-16T01:50:29Z`; recuperação automática de pagamentos continuou desativada.
- Spring Boot iniciado completamente; pool PostgreSQL e EntityManagerFactory inicializados; zero erros de schema e zero erros críticos nos logs examinados.
- Zero submissões de criação AliExpress nos logs da inicialização. Nenhuma reconciliação manual foi executada.

GETs verificados em 16/11/2026 às 00:44:16 (America/Sao_Paulo):

| Endpoint | Resultado |
| --- | --- |
| `/api/admin/health` | 200, `status=ok` |
| `/api/catalog?size=1` | 200, total de 2 produtos |
| `/api/catalog/categories` | 200, lista pública vazia conforme regra de categorias sem produtos classificados |
| `/api/catalog/categories/resolve?path=lavagem` | 200 |
| `/api/products?size=1` | 200 |
| `/api/admin/orders/21/aliexpress` | 200, CREATED, referência existente preservada |
| `/api/admin/orders/23/aliexpress` | 200, PENDING, sem nova tentativa |

Os pedidos #21 e #23 continuam excluídos da criação automática pelo corte anterior. A integridade das linhas existentes foi novamente confirmada após o reinício.

## Pendências e recuperação em caso de falha futura

Os produtos reais ainda precisam de classificação administrativa confirmada para preencher os menus de categorias. O seed não atribui características técnicas nem categorias aos produtos existentes. As restrições anteriores do checkout CJ continuam preservadas. A publicação do frontend não foi alterada nesta recuperação.

Antes do COMMIT, qualquer falha nas assertions interrompe o SQL e a conexão fechada desfaz a transação. Após COMMIT, não remover as estruturas novas como rollback improvisado: elas são aditivas. Se necessário, retornar ao código anterior mantendo o schema compatível; restauração do backup só mediante análise e autorização específicas.

Para publicações futuras com SQL manual, aplicar e verificar as migrações **antes** de iniciar o backend que exige o novo schema. Este relatório registra a aplicação desta versão, pois o projeto ainda não possui um executor/ledger automático de migrações.
