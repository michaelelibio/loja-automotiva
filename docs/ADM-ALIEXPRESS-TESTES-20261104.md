# ADM: testes isolados do AliExpress

Implementação local; nenhuma compra, migração em produção, commit, push ou publicação executada.

## Simulação

Antes de uma publicação futura, aplicar a migração aditiva
`backend/src/main/resources/db/migration/V20261104_01__admin_aliexpress_test_runs.sql`.
O projeto aplica migrações manualmente no perfil controlled. O novo registro fica em
`admin_aliexpress_test_runs`, sem modificar orders, payments ou order_fulfillments.

Identificar explicitamente os pedidos próprios de teste pela variável de backend
`ALIEXPRESS_ADMIN_TEST_ORDER_IDS`, uma lista de IDs separados por vírgulas. O padrão
é vazio. Nenhum ID de produção, inclusive #18, foi presumido como teste ou configurado
por esta implementação. A identificação vem apenas da configuração do servidor,
nunca de um campo enviado pelo navegador ou do checkout público.

Como ADMIN, abrir `/admin/pedidos/{id}`:

1. Clicar **Simular pagamento**: grava data e identidade administrativa uma única vez.
2. Clicar **Executar AliExpress simulado**: exige o primeiro passo e grava um ID
   `SIM-AE-{id}`, data e responsável. Repetir a chamada retorna o mesmo registro.
3. Reabrir o detalhe para conferir a persistência dos dois registros. O fornecedor
   simulado termina como criado e não pago. Não há chamadas OAuth, Mercado Pago,
   gateway AliExpress, emails, estoque ou transições financeiras nesse fluxo.

O pagamento financeiro continua com seu estado original. O fluxo normal de envio
AliExpress continua exigindo a confirmação financeira real que já exigia antes.
A simulação não funciona como credencial, permissão ou aprovação para compras reais.

## Operação real de teste: permanece bloqueada

O botão separado **Criar pedido real de teste no AliExpress** apresenta as pendências.
O backend recusa chamadas diretas pelos mesmos motivos. `controlled` bloqueia a
operação independentemente de flags; operações financeiras normais não foram alteradas.

`AliExpressPlaceOrderPermission` continua retornando NOT_VERIFIED. O novo
`AliExpressTestReadiness` também bloqueia explicitamente pela ausência de verificador
homologado para OAuth no fornecedor, assinatura, gateway, produto/SKU, modalidade de
entrega, valores, impostos e custos atuais. Um token local utilizável não comprova
esses requisitos. Não há opção administrativa que substitua essas provas por um checkbox.

**Limitação deliberada:** a criação real não pode ser habilitada nesta versão, mesmo
definindo as variáveis abaixo. Será necessário homologar e implementar a verificação
dessas evidências antes da primeira operação real. As chamadas de teste do caminho
real usam mocks, não demonstram autorização ou compatibilidade na plataforma.

A configuração preparada para essa homologação futura é:

- `ALIEXPRESS_ADMIN_TEST_REAL_ENABLED=false` por padrão;
- `ALIEXPRESS_ADMIN_TEST_FIXTURE_ORDER_ID`: único detalhe administrativo associado;
- `ALIEXPRESS_ADMIN_TEST_FIXTURE_JSON`: configuração privada com `id` estável,
  `authorizationReference`, `productId`, `skuId`, `skuAttr`, `quantity`, `deliveryService`,
  `currency=BRL`, `productCost`, `shippingCost`, `taxCost`, `totalCost` e `address` próprio.

`productCost` é o custo total dos produtos para a quantidade informada; total deve
ser produto + frete + impostos. O mapa `address` aceita apenas os campos de endereço
do placeorder definidos em `AliExpressTestFixture`. Não armazenar dados pessoais em
arquivos versionados. Conferir a propriedade e autorização do endereço na referência
privada antes da eventual homologação. Não copiar dados do pedido do cliente.

O serviço real constrói o payload exclusivamente dessa configuração do servidor.
O pedido do cliente fornece apenas o ID de associação do registro administrativo;
nenhum item, SKU, quantidade ou endereço do cliente entra no payload real de teste.
O navegador envia apenas a frase literal de confirmação, identificador idempotente
e digest da configuração exibida. Mudanças na configuração invalidam a confirmação.

Após homologação futura, a confirmação exigida será:
`CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS`.
Os bloqueios normais do cliente placeorder continuam sendo pré-requisitos adicionais.
Não existe chamada de pagamento ao AliExpress.

## Idempotência e conferência

O bloqueio `VERIFICATION_REQUIRED` é gravado e confirmado em transação independente
antes da única chamada placeorder. Um lock do pedido serializa solicitações, e
`real_key UNIQUE` impede o uso do mesmo identificador em outro detalhe. O identificador
não é rotacionado automaticamente nem substituído por um UUID a cada clique.

Sucesso registra todos os IDs retornados, responsável, data de envio e data da
resposta como `CREATED_UNPAID_UNVERIFIED`. Esse estado não comprova o status atual
no fornecedor. Conferir manualmente em **Minha conta → Meus pedidos → Aguardando pagamento**.

Timeout, resposta nula/incerta ou falha ao persistir o resultado deixam o marcador
anterior intacto. Recusa definitiva também não libera o identificador nesta operação
de teste. Não há retry automático, desbloqueio por tempo, exclusão de marcador ou
endpoint para liberar reenvio. Reconciliação e qualquer evolução desse fluxo exigem
uma operação posterior com evidência do fornecedor; não apagar registros nem trocar
o identificador para contornar incerteza.

## Revisão de segurança

- Rotas sob `/api/admin/**` e `@PreAuthorize(hasRole('ADMIN'))`.
- Lista de pedidos de teste configurada somente no backend, vazia por padrão.
- Simulação sem dependências financeiras e sem cliente remoto.
- Compra real independente de qualquer pagamento simulado.
- Configuração de destinatário não é retornada integralmente, nem registrada em logs.
- Confirmação vinculada ao digest de produto, SKU, quantidade, endereço, custos e autorização.
- Reserva durável antes de I/O; chave global única; bloqueio após incerteza e recusa.
- Nenhuma modificação em checkout público, frete ou estado dos pedidos anteriores.
- Alterações locais preexistentes de checkout/frete foram preservadas.

Testes automatizados cobrem autorização, simulação persistida, isolamento financeiro,
bloqueio de envio normal após simulação, confirmação/digest, controlled, provas ausentes,
payload exclusivo de teste, IDs retornados, concorrência, timeout e idempotência global.

## Validação local em 04/11/2026

- Backend: 70 testes aprovados, zero falhas/erros: `AdminAliExpressTestIntegrationTests`
  (12), `AliExpressOrderIntegrationTests` (33), `AliExpressOrderClientTests` (9),
  `AdminOrderIntegrationTests` (9) e `OrderLifecycleServiceTests` (7).
- Frontend: 68 testes aprovados; os 11 casos AliExpress foram repetidos após o ajuste final.
- TypeScript `tsc --noEmit` e ESLint nos arquivos alterados: aprovados.
- Compilação/testes Maven realizados em saída isolada devido ao cache/build parcial
  encontrado no ambiente. Log final: `backend/target/admin-aliexpress-final.log`.
- Backend validado em H2; a migração PostgreSQL não foi aplicada em ambiente externo.
- Nenhuma verificação real do fornecedor é alegada por esses testes.
