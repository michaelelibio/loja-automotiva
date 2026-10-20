# Frete AliExpress no checkout — auditoria de 20/10/2026

## Resultado e limite desta entrega

Evolução posterior em 20/10/2026: o diagnóstico administrativo foi adaptado para testar `ds.freight.query` com o OAuth persistido. Isso habilita a coleta de evidência da Etapa 1, sem liberar checkout ou avançar às integrações comerciais dependentes. Consulte [o procedimento atual, os testes e a inconsistência monetária oficial](ALIEXPRESS-DS-FREIGHT-DIAGNOSTIC.md). Permissão e retorno real BR continuam pendentes.

A auditoria identificou uma API oficial mais apropriada ao fluxo Dropshipping: `aliexpress.ds.freight.query`. O contrato documenta produto, SKU, quantidade e destino parcial, além de devolver um código de modalidade explicitamente utilizável na criação de pedidos. Isso corrige a conclusão histórica de que não havia evidência oficial desse método.

**A Etapa 1 ainda não está validada para avançar.** O administrador informou nesta tarefa que não possui confirmação das permissões da aplicação. Não há resposta autenticada de frete comprovadamente funcional, nem confirmação da cobertura de destino BR e de seus códigos territoriais. Não foi executada nenhuma consulta à conta AliExpress ou ao Railway. As Etapas 2, 3 e 4 permanecem pendentes conforme a sequência exigida; não foram implementadas integrações dependentes desses contratos ainda não validados.

A regra de independência da importação já está implementada: não existe dependência de cotação de frete em `AliExpressProductService`. Esta entrega acrescenta regressões que comprovam esse comportamento mesmo com os serviços de frete indisponíveis e verifica a preservação dos dados necessários ao fulfillment. Os bloqueios existentes de pedido e pagamento AliExpress foram mantidos.

## Auditoria do código atual

| Área | Implementado | Lacuna para o fluxo desejado |
| --- | --- | --- |
| Importação (`AliExpressProductService`) | Consulta `ds.product.get`, OAuth persistido, revalidação da prévia e persistência transacional de produto, imagens, custo/moeda, estoque, SKU, `sku_attr`, atributos e metadados; não consulta frete | Nenhuma dependência de frete a remover. Dados do fornecedor e país de destino não são confirmação de origem ou de frete |
| Carrinho (`CartContext`) | Produto/variante locais e quantidade; os IDs externos são resolvidos no backend | Não deve armazenar uma cotação comercial como se fosse permanente; cotação futura precisa corresponder ao carrinho efetivo |
| Checkout (`CheckoutForm`) | Cliente escolhe endereço; consulta frete com itens e variantes; limpa opções quando muda a chave da consulta | Hoje envia CEP e itens, sem `addressId` ao endpoint de frete. Chave usa ID/CEP, sem fingerprint do endereço completo. Resumo é visual e não representa cotação AliExpress confirmada |
| Frete (`ShippingService`, `CompositeShippingProvider`) | Resolve produto/variante, agrega linhas e valida produto; bloqueia AliExpress inclusive em carrinho misto antes de chamar outros provedores | Contrato interno recebe CEP, sem identidade do usuário/endereço completo. Não existe adaptador real para a API nova nem persistência de cotação |
| Diagnóstico ADM | `freight.calculate` real via backend, token persistido sem refresh, SKU e origem reconhecida; resposta sanitizada | Não é cotação comercial do checkout; opera por país, sem CEP. Sucesso nele não comprova permissão de `ds.freight.query` |
| Candidato interno (`AliExpressFreightQuote`) | BigDecimal, comparação de contexto, expiração local de 10 minutos e detecção de alteração de preço em testes | Somente objeto interno com gateway simulado nos testes; não é cotação persistida nem evidência de funcionamento real. Os 10 minutos são política local, não garantia de preço do fornecedor |
| Pedido (`OrderService`, `Order`, `OrderItem`) | Endereço pertencente ao usuário, snapshots de itens/endereço, subtotal e total calculados no servidor, seleção de frete novamente no servidor | Pedido recebe `shippingCode`, sem `quoteId`, versão, hash ou consumo da cotação. Falta idempotência de criação de pedido vinculada à operação do cliente; bloqueio visual não substitui idempotência no servidor |
| Pagamento (`PaymentAttemptService`) | Impede AliExpress sem frete confirmado, inclusive tentativa antiga; trava pedido/tentativa e reutiliza chave de idempotência do provedor | Não liberar substituindo o bloqueio por simples presença de modalidade. Deve validar cotação/contexto/validade/total antes de iniciar cobrança |
| Envio ADM (`AliExpressOrderService`, `AliExpressOrderPreparation`) | Somente após pagamento confirmado no backend, acionamento administrativo, referência externa persistida e bloqueio de reenvio ambíguo; pagamento ao fornecedor permanece manual | Preparação atual aceita somente uma remessa AliExpress para todo o pedido; não suporta corretamente opções diferentes por linha/origem. Há divergências de payload descritas abaixo |

## Contratos oficiais consultados

Leitura pública efetuada em 20/10/2026, sem credenciais. O arquivo `ALIEXPRESS-CHECKOUT-FREIGHT-EVIDENCE.json` contém o recorte dos campos, URLs, IDs documentais, exigência de autorização e hashes dos snapshots completos guardados em `target/checkout-contract-*.json`. Os exemplos Java desses métodos usam `Protocol.TOP`, compatível com o transporte `/sync`, `session`, HMAC-SHA256 e epoch milliseconds já conferido no SDK e utilizado para produtos. Não foram feitas tentativas autenticadas em gateways alternativos.

| Método | Evidência | Avaliação |
| --- | --- | --- |
| [aliexpress.ds.freight.query](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST) | docId 1579, título Delivery/Freight API, `authType=1` | Candidato principal para cotação por SKU no negócio Dropshipping; ainda sem prova de permissão/funcionamento para a inGarage |
| [aliexpress.logistics.buyer.freight.calculate](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST) | docId 525 | SKU documentado; país obrigatório, província/cidade opcionais; origem obrigatória; sem CEP. Diagnóstico existente não deve liberar checkout |
| [aliexpress.logistics.buyer.freight.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.get&methodType=GET%2FPOST) | docId 1442 | SKU obrigatório, destino parcial; sem CEP. Resposta monetária diferente de calculate; não é fallback automático |
| [aliexpress.trade.buy.placeorder](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.buy.placeorder&methodType=GET%2FPOST) | docId 479 | Método usado pelo cliente local; possui `sku_attr`, modalidade por item, endereço e `out_order_id` descrito para idempotência |
| [aliexpress.ds.order.create](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.order.create&methodType=GET%2FPOST) | docId 1615, título inclui criação e pagamento | Alternativa documental, sem autorização para migração automática. Expõe `try_to_pay`; o fluxo futuro deve manter pagamento automático ao fornecedor desativado explicitamente caso utilize esse método |

### Destino e variante em ds.freight.query

Parâmetro principal: `queryDeliveryReq`, objeto JSON. Obrigatórios: `quantity` (Number), `shipToCountry`, `productId`, `selectedSkuId`, `language`, `locale`, `currency` (Strings). Opcionais: `province`, `provinceCode`, `cityCode`.

- `province` e `provinceCode` são alternativas: o texto exige enviar **um ou outro**, não ambos. A descrição os relaciona a frete de produtos local-to-local.
- `cityCode` é opcional. O contrato lido não define um mapeamento brasileiro de cidade/código.
- Não há CEP, rua, número, bairro, complemento, nome, CPF, telefone ou data de nascimento no DTO de cotação.
- UF como `SP` não deve ser enviada como `provinceCode` sem confirmação do formato do fornecedor. Código IBGE não é automaticamente `cityCode` AliExpress.
- O destino completo deve ser validado e vinculado internamente à cotação futura; somente os campos suportados e validados poderão ser enviados ao fornecedor. Vincular um endereço internamente não aumenta a cobertura da API: uma cotação por país continua sendo uma cotação por país.
- SKU e produto são Strings no contrato: preservar IDs externos sem conversão para Number JavaScript.

O retorno documenta `result.code=200` para sucesso, `success`, `delivery_options` e, por modalidade: `code`, `company`, `ship_from_country`, `shipping_fee_cent`, `shipping_fee_currency`, `free_shipping`, limites de dias e descrições de estimativa. O `code` é descrito como código de entrega utilizado para criar o pedido; `company` é o nome de exibição. A origem nessa API é retornada por opção e não precisa ser presumida como CN nem fornecida no DTO.

### Precisão monetária e validade

`shipping_fee_cent` é String e sua descrição declara centavos; não se deve extrair valores de `shipping_fee_format` nem presumir unidades por heurística. A futura implementação deve validar uma string inteira não negativa, normalizar com BigDecimal e a escala comprovada para a moeda retornada, preservar valor original/moeda e rejeitar inconsistências com `free_shipping`. Para BRL, centavos permitem deslocamento decimal exato de duas casas. Sem política cambial validada, USD não pode ser somado silenciosamente ao subtotal BRL.

Não foi encontrado campo de expiração, identificador de reserva de preço ou garantia de preço no retorno inspecionado. Uma validade local serve apenas como limite da aplicação: será necessária reconsulta antes da confirmação, rejeitando alteração de preço, modalidade, moeda, carrinho ou destino. Não inventar prazo comercial do fornecedor. Prazos de entrega continuam estimados.

### Compatibilidade com criação de pedidos

O retorno de `ds.freight.query` fornece um código descrito para criação de pedidos; ambos os contratos de criação possuem `product_items[].logistics_service_name`. O candidato de mapeamento é **o código da opção**, e não `company`/nome de exibição. O comportamento exato para o app deve ser validado antes de habilitar a operação; não foi criado pedido para testar isso.

No código atual, `AliExpressOrderPreparation` utiliza `shippingLeg.name()` como modalidade e aplica a mesma remessa a todos os itens. Isso precisa mudar para o código bruto confirmado e para o vínculo por linha/remessa, após a Etapa 2.

Outro bloqueio encontrado: a preparação local coloca `birth_date` no endereço. O contrato atual de `trade.buy.placeorder` documenta a data BR em `ds_extend_request.trade_extra_param.birthday` dentro do objeto principal; `ds.order.create` coloca essa extensão em parâmetro separado. O formato documentado é `ddMMyyyy`. Não mover campos nem trocar operação por tentativa; a correção deverá fazer parte da Etapa 4, com testes de contrato, mantendo dados pessoais fora de logs/DTOs públicos.

`out_order_id` está documentado para idempotência, mas não basta assumir reconciliação ou autorização. Timeout/resposta ambígua deve continuar bloqueando reenvio até verificar o resultado no fornecedor. Esta entrega não chama nenhuma operação de criação/pagamento.

## Plano condicionado aos marcos anteriores

1. **Concluir Etapa 1:** comprovar grants da aplicação para a consulta escolhida e para o método de pedido; obter confirmação oficial de cobertura por destino BR, códigos territoriais e uso do código de entrega. O administrador confirmou que não dispõe dos grants nesta tarefa. Documentação pública e OAuth válido não substituem essa comprovação.
2. **Etapa 2, após o marco:** implementar adaptador exclusivo de `ds.freight.query` e ensaio restrito/sem pagamento, reutilizando OAuth persistido; validar dados reais de SKUs e destinos BR. Consultar cada linha separadamente até haver contrato comprovado de consolidação. Não presumir que fretes unitários possam ser somados como remessa única, que existe desconto combinado ou que a ausência de opção equivale a frete grátis.
3. **Etapa 3, após cotação real comprovada:** endpoint autenticado com `addressId`, itens, `variantIds` e quantidades; resolver endereço pertencente ao usuário e códigos do fornecedor no backend. Persistir cotação com proprietário, contexto completo, cobertura real de destino, remessas/opções, códigos brutos, valores/moedas, timestamps, expiração local e versão/hash. Exigir `quoteId` e escolhas por remessa; revalidar total com BigDecimal, preço e disponibilidade. Consumir a cotação de forma transacional/idempotente. Rejeitar mudanças e expiração em vez de cobrar valor divergente. Exibir subtotal, frete e total confirmados pelo backend. Não alterar frete de pedidos já pagos.
4. **Etapa 4, após o marco de checkout:** persistir em cada item/remessa o código confirmado e o contexto, corrigir o payload documental de destinatário, adaptar envio administrativo às remessas e revalidar a elegibilidade. Pagamento do cliente confirmado precede o envio manual ADM; pagamento ao fornecedor permanece manual. Preservar referência idempotente e reconciliação de resultados ambíguos.

Se o fornecedor não confirmar cobertura suficiente do destino, o checkout AliExpress permanece bloqueado mesmo que uma consulta por país retorne preço. O produto pode continuar sendo importado e revisado no catálogo.

## Alterações desta entrega e validações

- Comentários do serviço corrigidos: SKU existe nos contratos atuais; o bloqueio decorre da ausência de validação de destino/permissões e da integração comercial ainda incompleta.
- Importação mantida sem frete e sem remoção de metadados. Três novos casos de regressão exercitam importação com serviços de frete negado, indisponível e não configurado, sem qualquer chamada a eles, verificando persistência de custo, estoque, imagens, atributos, SKU/sku_attr e metadados.
- Nenhuma mudança funcional de checkout, pedidos, pagamento, OAuth ou configuração de produção; nenhum commit, push ou deploy.
- Validação backend: 161 testes aprovados em 10 classes de importação, frete, checkout/destinatário e pedidos; zero falhas/erros/ignorados, Maven exit 0. Inclui os três novos casos de regressão da importação.
- Validação frontend: 39 testes aprovados, incluindo importação e bloqueio de checkout sem frete confirmado.
- `git diff --check`: sem erros de whitespace.
- Testes usam respostas sintéticas/H2; não representam prova de cotação real. Não houve alteração de frontend que exigisse nova compilação/lint nesta etapa.
