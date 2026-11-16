# Compra, variantes e destinatário

O catálogo mantém os IDs originais de produto e SKU. A apresentação traduz os atributos conhecidos, ignora auxiliares `:name`/`:valueId`, oculta escolhas constantes e seleciona automaticamente a única combinação comprável. Combinações indistinguíveis ou indisponíveis não liberam a compra. Preço de venda e estoque vêm do backend; custo do fornecedor não é usado como preço de venda.

A adição ao carrinho aguarda sua restauração e abre confirmação acessível. O checkout abre um resumo antes de enviar o pedido, bloqueia chamadas simultâneas e mantém a chave de idempotência em tentativas do mesmo contexto. Nenhuma dessas confirmações realiza pagamento.

## Dados salvos por endereço

`GET/PUT/DELETE /api/addresses/{id}/recipient` exigem o titular autenticado. A leitura administrativa usa uma rota separada, `GET /api/admin/addresses/{id}/recipient`, restrita a ADMIN. Respostas usam `Cache-Control: private, no-store`; erros não reproduzem os valores pessoais.

CPF, nascimento e telefone são validados, criptografados juntos com AES-256-GCM e nonce aleatório, e autenticados com o ID do titular, ID do endereço e digest do destinatário/endereço. Alterar a identidade ou o endereço invalida/remove o perfil salvo. Os dados podem ser editados no checkout; a opção de salvar é informada e pode ser desmarcada. A remoção do perfil não altera o endereço nem os snapshots de pedidos existentes. Nenhum pedido antigo é usado para preencher o perfil novo.

Não guardar esses campos em localStorage, sessionStorage, URLs ou logs. O resumo temporário de pedido não é mais gravado no navegador. A lista pública de endereços não retorna dados fiscais.

## Publicação controlada

O projeto utiliza migrações SQL manuais; Hibernate permanece em `validate` em produção. Antes de publicar a entidade nova:

1. Confirmar banco/schema do backend e estado das integrações. Não mudar flags, corte UTC ou recuperar pedidos antigos.
2. Fazer backup lógico real em snapshot consistente, protegido com criptografia e ACL. Restaurar em PostgreSQL isolado e conferir contagens e fingerprints das linhas, constraints, índices e sequências. Registrar as limitações de recuperação da proteção utilizada.
3. Aplicar `backend/src/main/resources/db/migration/V20261116_02__address_recipient_profiles.sql` em PostgreSQL local, duas vezes, e conferir que somente a tabela nova foi acrescentada e que todas as linhas anteriores foram preservadas.
4. Em produção, conferir se a tabela existe e se tem exatamente a estrutura esperada. Em transação, com `lock_timeout` e `statement_timeout`, aplicar o arquivo completo e validar antes do COMMIT: cinco colunas obrigatórias, PK em `address_id`, FK para `addresses(id)` com `ON DELETE CASCADE`, comprimentos 64/2048, timestamps com fuso, tabela nova vazia e fingerprints das tabelas anteriores inalterados. Divergência provoca ROLLBACK. Não inserir dados pessoais nem fazer backfill.
5. Configurar somente a variável de servidor `RECIPIENT_DATA_ENCRYPTION_KEY`, com 32 bytes aleatórios em Base64, sem registrar seu valor e sem acionar deploy antecipado. Guardar cópia protegida da chave separadamente do backup do banco e verificar a recuperação. Nunca usar uma variável `NEXT_PUBLIC_*` para ela. Preservar integralmente `SPRING_APPLICATION_JSON` e as demais variáveis.
6. Publicar o commit na main e acompanhar Railway e Vercel. Conferir SHA, inicialização, Hibernate `validate`, health, catálogo e autorização das rotas novas por leitura. Testes de criação/pagamento ficam exclusivamente em mocks.

Não trocar essa chave sem planejar a recriptografia dos perfis existentes. Uma restauração do banco exige também a chave correspondente. Uma cópia protegida por DPAPI CurrentUser exige o mesmo usuário/perfil Windows; isso não substitui uma cópia externa portátil com procedimento de recuperação definido.

## Validação de 16/11/2026

- Backend completo: `mvn verify`, 1.029 testes, zero falhas/erros; 914 aprovados e 115 testes condicionais de ambientes externos desativados. O pacote final também passou por 18 testes direcionados de destinatário, criptografia, seleção de SKU e checkout, incluindo administrador desativado/permissão revogada.
- Frontend: 105 testes aprovados, TypeScript e build sem erros; lint sem erros, com cinco avisos preexistentes.
- Chrome isolado com API sintética: 1440, 390 e 320 px, sem overflow horizontal nos modais; seleção automática, combinações válidas, estoque e imagem, foco/Escape, adição, revisão, cancelamento, confirmação repetida gerando somente um pedido simulado, zero pagamentos, preenchimento por endereço, edição, remoção e ausência dos campos pessoais no armazenamento do navegador.
- Backup real restaurado em PostgreSQL 18 local; migração em duplicidade e preservação de todas as linhas anteriores conferidas. Produção: migração aplicada em transação, com preservação por fingerprint das 26 tabelas anteriores, incluindo 25 pedidos, 18 pagamentos e 13 fulfillments; tabela nova vazia, sem backfill. Chave dedicada configurada com cópia protegida e recuperação verificada; nenhuma outra variável alterada.
- A conferência do catálogo encontrou oito variantes legadas com seleção não verificada. Cada produto/SKU foi consultado na API do fornecedor: todos foram confirmados como SKU único sem atributos de escolha. A correção pontual registrou somente essa ausência confirmada, preservando IDs e preços; um estoque foi reduzido de 12 para 11 conforme a consulta atual. As demais variantes, os pedidos, pagamentos e snapshots históricos foram preservados. Nenhuma compra ou pagamento real foi feito na validação.

## Recuperação

Antes do COMMIT, qualquer falha interrompe a transação e faz rollback. Depois do COMMIT, a tabela adicional é compatível com o backend anterior: em caso de regressão, retornar ao commit anterior mantendo a tabela e a chave, sem apagar perfis salvos e sem restaurar automaticamente o banco. Reavaliar os logs e reconciliar o estado antes de outra publicação. Não usar `ddl-auto=update`.
