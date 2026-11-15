# inGarage — checkout limpo e pedidos AliExpress

Data: 30/10/2026. Alterações locais, sem commit, push, deploy, pagamento ou compra real.

## Resultado e limites

- Checkout apresenta produtos, quantidades, endereço cadastrado completo, dados do destinatário, entrega e total. A confirmação leva ao detalhe do pedido existente.
- Saiu da interface operacional a solicitação de cotação, o cálculo de frete de produto/carrinho, a tabela por UF e o diagnóstico de frete do ADM. A rota `/admin/fretes` responde como página inexistente.
- O backend exige entrega grátis comprovada para endereço completo, produto e SKU do fornecedor, quantidade e preço. Evidência ausente, vencida ou divergente bloqueia a compra. Não consulta APIs de cálculo de frete.
- O catálogo público oferece somente produtos AliExpress ativos com evidência vigente. O checkout confirma o destino específico. Sem evidência configurada, o catálogo fica vazio; não há produtos de demonstração substituindo a resposta do servidor no checkout ou carrinho.
- Pedido interno mantém snapshots, preço calculado no servidor, chave de idempotência e status `PENDING_PAYMENT`. Nenhum pagamento é marcado como aprovado pelo checkout.
- Simulação cobre confirmação pelo atualizador de webhook, ID AliExpress persistido, estado `AWAITING_MANUAL_PAYMENT`, detalhe do cliente e leitura no ADM. Reenvio retorna o vínculo existente. Os testes existentes cobrem concorrência e resultado ambíguo, bloqueando novas tentativas até reconciliação.
- Pagamento ao fornecedor continua manual. Criação real de pagamento e envio real continuam bloqueados.

Isso valida o fluxo local com adaptadores simulados. Não constitui homologação de Mercado Pago, autorização efetiva AliExpress ou promessa de cobertura nacional.

## Git e banco antes das mudanças

Branch encontrada: `release/cotacao-assistida-controlada`, HEAD `437e891`, acompanhando a branch remota de mesmo nome. `main` estava em `ebaee6c`. Nenhuma branch foi trocada.

Já havia alterações locais em OrderService/OrderRepository, serviços e testes de frete comercial, checkout, tabela administrativa, calculador, testes frontend e `next-env.d.ts`; além de documentos/evidências e componentes não rastreados. Esses trabalhos foram preservados. A página administrativa anterior foi guardada em `frontend/archive/freight/page.tsx.disabled`, incluindo seu conteúdo local.

O projeto usa inicialização SQL e Hibernate; não foi encontrado Flyway no `pom.xml`. Foram inspecionados os scripts existentes até `V20261030_02__commercial_freight.sql`, incluindo as colunas `assisted_quote`, `request_key`, `commercial_shipping` e a tabela de referências manuais. Não houve alteração ou nova migração. O estado aplicado no Railway não foi consultado e não deve ser presumido a partir dos arquivos do repositório.

O fluxo anterior foi isolado em `LegacyOrderCheckout`, disponível apenas com `local-freight`, sem os perfis `controlled`, `production` ou `prod`. Os controladores de cálculo, cotação e diagnóstico de frete receberam a mesma restrição. Os testes de regressão históricos usam esse perfil local; os testes do checkout novo ativam explicitamente `clean-checkout`. Não ativar `local-freight` no Railway.

## Evidência de entrega grátis

Configuração: `VERIFIED_FREE_DELIVERY_EVIDENCE`, vinculada a `app.delivery.verified-evidence`, vazia por padrão (`[]`). É uma lista JSON de confirmações offline do fornecedor; não é uma tabela de tarifas nem um cálculo.

Cada confirmação exige:

- `reference`: referência rastreável ao comprovante do fornecedor;
- `addressDigest`: SHA-256 do JSON canônico produzido por `VerifiedFreeDelivery.addressDigest`, incluindo BR, destinatário, CEP, rua, número, complemento, bairro, cidade e UF;
- `confirmedAt` e `validUntil`: instantes ISO-8601 válidos, com confirmação no passado e validade futura;
- `supplierShippingAmount`: zero efetivamente confirmado; `currency`: `BRL`;
- `items`: composição exata, com `productId`, `variantId`, `supplierProductId`, `skuId`, `quantity`, `unitPrice`, `logisticsCode`, `logisticsName`, `originCountry` e `estimatedDays`.

Não cadastrar zeros por inferência, omissão de modalidade ou simples presença de um selo no anúncio. A confirmação precisa cobrir o destino e a composição integral do pedido. A obtenção e revisão operacional dessas evidências reais continuam pendentes; nenhum comprovante fictício foi cadastrado na configuração de produção.

A evidência é conferida novamente na criação do pedido, antes da preferência de pagamento e antes do envio ao fornecedor. O vencimento do pedido/pagamento é limitado à validade da evidência. Mudanças ou expiração posteriores bloqueiam a continuação e exigem tratamento operacional.

## Contrato oficial de placeorder

Fonte consultada: [Alibaba — aliexpress.trade.buy.placeorder, API 35446](https://developer.alibaba.com/doc2/apiDetail.htm?apiId=35446).

| Campo | Contrato publicado |
| --- | --- |
| `param_place_order_request4_open_api_d_t_o` | DTO obrigatório |
| `logistics_address` | Obrigatório |
| `logistics_address.address` | Obrigatório |
| `product_items` | Obrigatório |
| `product_id` | Número obrigatório |
| `product_count` | Número obrigatório |
| `sku_attr` | Opcional no contrato; exigido pela política interna para identificar a variante exata |
| `logistics_service_name` | Opcional no contrato; enviado a partir da modalidade comprovada |
| Valor do frete | Não consta campo obrigatório para envio desse valor |

Os demais campos brasileiros são enviados para representar o endereço completo e a identificação fiscal exigida para a operação. A ausência de campo de valor de frete ou a omissão de modalidade não comprovam gratuidade.

Foram retirados do payload `out_order_id` e `ds_extend_request`, que não aparecem nessa página oficial. A referência interna continua persistida localmente para controle e reconciliação; não é alegada idempotência remota. Data de nascimento permanece no cadastro/snapshot legado, mas não é enviada por um parâmetro não comprovado.

O transporte anterior utiliza o gateway AliExpress `/sync`, SHA-256 e timestamp em milissegundos; a página consultada descreve o gateway TOP, algoritmos `hmac`/`md5` e timestamp formatado GMT+8. A compatibilidade do transporte existente com a autorização do app **ainda precisa ser homologada**, sem deduzir equivalência entre os gateways.

## Autorização real AliExpress

Resultado: **NOT_VERIFIED**. Não foi feita chamada de criação para sondar permissão, nem acessada a autorização da conta de produção. OAuth localmente legível não prova permissão para `placeorder`.

`AliExpressPlaceOrderPermission` retorna bloqueio por padrão e não possui flag que libere silenciosamente a operação. É conferido tanto na preparação quanto na construção do cliente real. A liberação exige implementar e validar a fonte de comprovação efetiva do método; ligar as duas flags antigas não basta.

O OAuth persistido continua sendo usado pelo serviço existente. A consulta administrativa de autorização permanece disponível, sem refresh ou exposição de credenciais, e há status específico em `GET /api/admin/integrations/aliexpress/placeorder-authorization`.

## Pendências Mercado Pago

1. Homologar com vendedor/comprador de teste separados e credenciais de sandbox, sem reutilizar credenciais de produção.
2. Conferir URLs HTTPS de sucesso, pendência, falha e notificação, origens CORS e endereço público Vercel/Railway.
3. Validar a assinatura do webhook com o segredo da aplicação e a consulta backend do pagamento; testar aprovado, pendente, recusado, notificação repetida e fora de ordem.
4. Confirmar associação de pagamento, referência externa, moeda BRL, valor integral e vencimento limitado à evidência de entrega.
5. Definir tratamento operacional para aprovação tardia, mudança de disponibilidade e cancelamento/reembolso. Não liberar envio de pedido expirado ou com evidência vencida.
6. Manter `PAYMENT_CREATION_ENABLED=false` até a aprovação operacional separada. O perfil `controlled` fixa esse bloqueio.

## Pendências para pedido AliExpress real

1. Comprovar autorização efetiva do método no app inGarage e implementar a verificação em `AliExpressPlaceOrderPermission`.
2. Homologar gateway, assinatura, token OAuth e contrato BR usando documentação correspondente ao app. A simulação HTTP não comprova aceitação pelo fornecedor.
3. Confirmar produto/SKU, estoque atualizado, preço do fornecedor, moeda, impostos, custo integral, endereço brasileiro, CPF e necessidade dos demais dados fiscais.
4. Cadastrar evidências reais de entrega grátis por pedido/destino e validar a modalidade logística. Nenhuma condição desconhecida pode ser convertida em custo zero.
5. Validar reconciliação por IDs e referência interna, incluindo timeout após possível criação. Não presumir deduplicação remota por `out_order_id`.
6. Homologar acompanhamento/rastreamento real. A interface atual oferece estados internos; esta entrega não adicionou consulta automática de tracking.
7. Só após essa validação considerar habilitar `ALIEXPRESS_ORDERS_ENABLED` e `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED`. Não há pagamento automático ao fornecedor.

## Arquivos desta entrega

- Backend: `order/service/OrderService.java`, novo `LegacyOrderCheckout.java`, `order/entity/Order.java`, `payment/service/PaymentAttemptService.java`, `product/service/ProductService.java`.
- Entrega: novos `shipping/eligibility/VerifiedFreeDelivery.java` e `DeliveryEligibilityController.java`; `shipping/provider/ShippingProvider.java` e `shipping/service/ShippingService.java`.
- Integração: `AliExpressOrderPreparation.java`, `AliExpressOrderClient.java`, novos `AliExpressPlaceOrderPermission.java` e `AliExpressOrderAuthorizationController.java`.
- Isolamento: `AliExpressDiagnosticsController.java`, `AssistedQuoteController.java`, `CommercialFreightController.java`, `ShippingController.java`.
- Configuração: `backend/src/main/resources/application.properties`, recursos de teste e regras locais de ignore.
- Frontend: checkout, carrinho, compra do produto, detalhe de pedido cliente/ADM, listagem administrativa de produtos, página de fretes, página de entrega, `AdminShell`, campos de destinatário e API de elegibilidade em `lib/api/shipping.ts`.
- Preservação: `frontend/archive/freight/page.tsx.disabled`.
- Testes: novos `CleanCheckoutIntegrationTests`, `CleanCheckoutPostgresTests` e `CleanCheckoutSafetyTests`; atualizados testes AliExpress de cliente, destinatário e envio, `checkout-recipient.test.cjs` e `assisted-quote.test.cjs`.

Os demais arquivos sujos do Git já continham trabalho anterior e não devem ser atribuídos integralmente a esta entrega.

## Publicação controlada

1. Revisar os diffs locais, incluindo os trabalhos anteriores de frete. Fazer commit/push somente mediante ação autorizada posterior.
2. Fazer backup e conferir o esquema real do PostgreSQL. Esta entrega não exige migração. Se houver coluna histórica ausente, aplicar apenas a migração aditiva correspondente após revisão; não repetir scripts sem necessidade.
3. Railway: usar `controlled`, `spring.jpa.hibernate.ddl-auto=validate` e `spring.sql.init.mode=never`, já definidos nesse perfil. Manter frete comercial, pagamentos reais, criação real AliExpress e simulação DSers desabilitados. Não ativar `local-freight`.
4. Cadastrar somente evidências reais e vigentes; verificar o catálogo resultante e os bloqueios por destino, SKU, quantidade e vencimento. Ausência de evidência deve continuar bloqueando vendas.
5. Publicar backend e frontend compatíveis em janela coordenada. O frontend novo usa `POST /api/orders/delivery-eligibility` e envia `VERIFIED_FREE`, chave de idempotência, total e endereço aceitos. A versão antiga de checkout deixa de ser compatível com a política nova.
6. Vercel: conferir `NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_API_URL`; Railway: conferir `FRONTEND_URL` e `CORS_ALLOWED_ORIGINS`, com as origens HTTPS corretas.
7. Validar leitura do catálogo, indisponibilidade sem comprovação, criação interna, detalhe do cliente e ADM mantendo pagamentos indisponíveis. Não gerar compra ou cobrança real como smoke test.
8. A liberação de Mercado Pago e AliExpress é uma etapa operacional posterior. Reverter interface exige também avaliar compatibilidade da política backend; não reabrir automaticamente cotações nem fazer rollback destrutivo do banco.

## Validação

- Backend: execução completa com **778 cenários, zero falhas/erros, 35 opcionais ignorados**. Após os últimos ajustes, foram repetidos os testes afetados de checkout, AliExpress, pagamentos e webhooks. Os relatórios finais reúnem **785 cenários: 750 executados com sucesso e 35 ignorados**, sem falhas ou erros.
- Checkout novo: **6 cenários em H2 e os mesmos 6 em PostgreSQL 18 local**, incluindo pedido pendente, idempotência, pagamento simulado confirmado, vínculo AliExpress, leitura cliente/ADM, evidência ausente/vencida, endereço/total alterados, códigos antigos, estoque e propriedade do endereço. Há ainda um teste de segurança que impede cobrança de pedido legado no perfil controlado, mesmo com criação de pagamento habilitada no objeto simulado.
- PostgreSQL foi inicializado em diretório descartável `.checkout-pg`, usuário de teste `checkout_test`, banco `clean_checkout`, somente em `127.0.0.1:55449`. Não usa `DB_URL` ou credenciais de produção. A instância foi encerrada ao final; os arquivos locais ficam ignorados pelo Git.
- Frontend: **62 testes aprovados**, build Next.js de produção e TypeScript aprovados; lint sem erros e com 5 avisos sobre imagens, dependências de hook e exportações existentes.
- `git diff --check`: aprovado. Nenhuma migração foi alterada ou executada em produção.

O wrapper Maven e o cache padrão apresentaram problemas de acesso no ambiente de execução, e o `target` estava ocupado. Os testes usaram o Maven 3.9.16 já instalado, cache local existente e um POM temporário ignorado (`.checkout-validation.pom.local`) que muda somente o diretório de build para `target-checkout-validation`. Não foi necessário mudar o `pom.xml` do projeto.

Não houve validação manual autenticada em navegador nem execução contra contas reais. A validação integral foi feita por testes HTTP/serviço, renderização de componentes e adaptadores simulados. A evidência de autorização real continua pendente.
