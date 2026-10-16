# Etapa 2 — investigação de frete e bloqueio seguro

## Resultado

**Cotação real não habilitada.** Não existe evidência do contrato/permissão de frete específico da aplicação inGarage. O preview de produto funcionar não comprova autorização para outra API. Nenhuma chamada ao fornecedor ou compra foi executada nesta etapa.

Fonte oficial consultada em 15/10/2026: https://developer.alibaba.com/doc2/apiDetail.htm?apiId=39371 (`aliexpress.logistics.buyer.freight.calculate`).

A referência exige autorização de usuário e o objeto `param_aeop_freight_calculate_for_buyer_d_t_o`. Os campos obrigatórios são `country_code`, `product_id`, `product_num` e `send_goods_country_code`. `city_code` e `province_code` são códigos do fornecedor, não nomes de cidade ou UF. `price` e `price_currency` são opcionais. A referência não documenta CEP nem SKU nesse objeto. Não adicionar parâmetros presumidos nem usar preço de venda como preço do fornecedor. O país de origem não pode ser deduzido do país de destino ou da descrição do produto.

O retorno documenta sucesso, lista de modalidades, `service_name`, `estimated_delivery_time` e `freight.amount`, `cent`, `currency_code`. Prazo é estimado. O exemplo oficial mostra valores inconsistentes entre `amount` e `cent`; é necessário confirmar a representação monetária efetiva, sem arredondar ou escolher silenciosamente uma informação conflitante. O exemplo em USD não comprova retorno em BRL. Não há conversão ou frete gratuito presumidos.

## Alterações implementadas

- O provedor composto não trata mais itens de fulfillment AliExpress como entrega local. Carrinhos mistos também são bloqueados antes de consultar CJ ou frete local.
- `AliExpressFreightService` retorna conflito com mensagem fixa e segura. Não oferece flag que transforme um contrato não validado em cotação confirmada.
- Conforme orientação do responsável, há uma interface interna `AliExpressFreightGateway` e um candidato imutável `AliExpressFreightQuote`, exercitados somente com respostas simuladas. Esses tipos NÃO representam parâmetros HTTP AliExpress. Validam origem explícita, IDs, quantidade, destino, modalidades distintas, BRL e valores sem arredondamento, estimativas e validade interna de dez minutos (política local, não validade confirmada pelo fornecedor). O candidato vincula o endereço completo e os itens, rejeita alterações/expiração e compara custo/serviço com nova cotação para exigir revisão. Endereço é ocultado em `toString`. Falhas do gateway viram mensagem fixa sem causa sensível.
- O candidato não é cotação autorizada para pagamento: falta persistência com propriedade do usuário, seleção e snapshot do pedido. O adaptador padrão é indisponível, e o caminho de checkout permanece bloqueado mesmo diante de candidatos simulados válidos.
- Novos pedidos AliExpress não passam da seleção de frete, mesmo que o navegador envie `STANDARD` ou valores comerciais falsos.
- Pedidos AliExpress antigos, ainda não pagos, também não podem iniciar ou reutilizar uma preferência de pagamento sem cotação confirmada. A proteção ocorre antes da criação da tentativa e novamente antes do gateway. Pedidos já pagos e webhooks não são modificados.

Esse bloqueio impede novas autorizações pelo fluxo de criação de pagamento do backend; não cancela links de pagamento que já tenham sido emitidos pelo gateway antes desta alteração. Eventuais links históricos precisam de análise separada, sem cancelar pagamentos ou alterar pedidos automaticamente nesta etapa.
- Checkout explica a necessidade de frete confirmado e mantém o bloqueio sem modalidades válidas. Produtos convencionais e CJ preservam seus provedores existentes.
- A criação real de ordens continua desabilitada conforme a etapa anterior.

## Pendências para concluir a funcionalidade

1. Obter no console da aplicação a autorização efetiva para o método de frete e sua documentação atual. Fornecer apenas documentação ou resposta anonimizada, nunca credenciais.
2. Confirmar um contrato que cote a variante, quantidade e destino brasileiro com CEP, além da origem real de cada variante. Se outro método oficial for necessário, validar explicitamente seu contrato e permissão antes de implementá-lo.
3. Confirmar moeda, unidades monetárias, validade da cotação, limites e significado do prazo. Obter respostas anonimizadas de sucesso com múltiplas modalidades, indisponibilidade e erro.
4. Implementar o cliente HTTP e a normalização com essas evidências. Somente então oferecer modalidades selecionáveis no checkout.
5. Persistir cotação no servidor vinculada ao usuário, endereço, produto, variante e quantidade, com expiração. Validar a seleção sem confiar em valores do navegador e invalidar alterações de endereço/itens/quantidade.
6. Gravar snapshot completo no pedido: serviço real, valor/moeda, estimativa, identificadores, instante da cotação e vínculos acima. O `shipping_legs` atual não contém todos esses dados. Nenhuma migração foi criada nesta etapa porque ainda não há contrato confirmado para essa persistência.
7. Antes de criar ordem, recotar e comparar serviço/custo com o snapshot pago. Divergências exigem revisão administrativa, sem alterar o total pago. Mapear o identificador confirmado para `logistics_service_name`. Não habilitar compras reais nesta etapa.

Os testes simulados cobrem candidato válido, múltiplas modalidades, ausência de frete, valores/moedas inválidos, expiração, alterações de endereço/quantidade, mudança de custo e falhas de comunicação sem vazamento. Há testes do bloqueio por falta de confirmação, carrinho misto, valores forjados na criação do pedido, pagamentos e compatibilidade dos provedores existentes. Não representam comprovação de integração real nem substituem os testes de persistência/snapshot ainda pendentes.

## Arquivos desta etapa

- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightService.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightGateway.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightQuote.java`
- `src/main/java/com/garage/garageapi/shipping/provider/CompositeShippingProvider.java`
- `src/main/java/com/garage/garageapi/payment/service/PaymentAttemptService.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightSafetyTests.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightQuoteTests.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressCheckoutRecipientIntegrationTests.java`
- `src/test/java/com/garage/garageapi/shipping/ShippingIntegrationTests.java`
- `../frontend/app/checkout/CheckoutForm.tsx`
- `../frontend/tests/checkout-recipient.test.cjs`
- Este documento.

Não houve alteração de OAuth, credenciais, importação, ativação de produtos, checkout convencional ou processamento de webhooks. Nenhum commit, push ou deploy.

## Validações executadas

- Backend: suíte completa com 481 testes, zero falhas/erros (`target/freight-full-suite.log`). Após os ajustes finais, reexecução específica de frete, destinatário e pagamentos com 47 testes, todos aprovados (`target/freight-final-check.log`).
- Frontend: 28 testes aprovados; `npx tsc --noEmit` aprovado.
- ESLint dos arquivos frontend desta etapa: zero erros; permanece o aviso anterior de `<img>` no checkout. Lint global: dois erros anteriores de `any` em `lib/api/favorites.ts:36`, além de avisos existentes.
- Build Next: bloqueado ao baixar Manrope e Space Grotesk do Google Fonts; nenhuma alteração de fontes realizada.
- `git diff --check`: aprovado.
