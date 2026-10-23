# Investigação ALIEXPRESS_AMBIGUOUS_MONEY — 23/10/2026

## Causa e limite da evidência

O 409 informado comprova rejeição local de moeda, mas não identifica sozinho qual campo/valor chegou do Railway. Nenhuma resposta autenticada de produção foi fornecida ou consultada nesta tarefa. Não é correto afirmar que o Railway recebeu `1.99` apenas porque esse é o exemplo oficial.

`AliExpressDsFreightClient.parse` percorre `result.delivery_options`. Rejeita `shipping_fee_cent` decimal com `AMBIGUOUS_MONETARY_UNIT`; também usa esse código se o formato decimal simples reconhecido em `shipping_fee_format` divergir de `shipping_fee_cent / 100`. Inconsistência entre `free_shipping` e valor zero gera `CONFLICTING_MONETARY_FIELDS`. `AliExpressDsFreightGateway.rejected` converte ambos em `AliExpressShippingException(AMBIGUOUS_MONEY)`. O handler global devolve HTTP 409 / `ALIEXPRESS_AMBIGUOUS_MONEY`.

## Contrato e SDK

Fonte oficial DS: https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST

A tentativa de releitura pública em 23/10 falhou por conectividade. Foram inspecionados o snapshot oficial local `target/checkout-connected-contract.json`, o recorte versionado `ALIEXPRESS-DS-FREIGHT-DIAGNOSTIC-EVIDENCE.json` e o código do SDK oficial em `target/ds-freight-TopExecutor.java`. A evidência versionada inclui URL e SHA256 do SDK anteriormente obtido. Não se afirma atualização do contrato nesta data.

O exemplo Java usa `IopRequest`, `queryDeliveryReq` e `client.execute(request, accessToken, Protocol.TOP)`. O SDK genérico transporta a resposta; não fornece conversão monetária que resolva a contradição. Os exemplos de resposta simplificada e completa repetem `shipping_fee_cent="1.99"`, `shipping_fee_format="US $1.99"`, `shipping_fee_currency="USD"`. A descrição do primeiro campo diz centavos. Tipo String não comprova a escala e o nome de um campo isoladamente também não resolve essa divergência.

| Campo em delivery_options | Tipo declarado / exemplo | Evidência da unidade e tratamento |
|---|---|---|
| shipping_fee_cent | String / String | Descrição declara centavos (1/100); exemplo decimal conflita. Inteiros continuam sendo convertidos exatamente pelo parser existente com BigDecimal.movePointLeft(2). Decimais permanecem bloqueados. |
| shipping_fee_format | String / String | Texto de apresentação; nunca fonte de preço. Diagnóstico mostra somente decimal simples extraído quando reconhecido, sem copiar texto. |
| shipping_fee_currency | String / String | Moeda do frete; não determina a unidade nem autoriza câmbio. |
| free_shipping | Boolean / String "false" | Indicador de gratuidade; não é valor monetário. Divergência com zero bloqueia. |
| free_shipping_threshold | String / String "$10.00" | Limiar, não preço do frete; escala não estabelecida para conversão. Valor suprimido. |
| shipping_fee | Ausente no contrato DS inspecionado | Sem escala comprovada; presença bloqueia. Objetos têm somente filhos monetários conhecidos inspecionados. |
| amount | Ausente no contrato DS inspecionado | Não implica unidade principal; presença bloqueia. |
| cent | Ausente no contrato DS inspecionado | Não herda escala de DTOs das APIs legadas; presença bloqueia. |
| currency_code | Ausente no contrato DS inspecionado | Não substitui shipping_fee_currency; presença bloqueia. |

No parser Jackson, tipos efetivos são agora registrados como STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL ou MISSING antes de se perderem na leitura `asText()`. Os testes locais anteriores continham valores sintéticos inteiros, strings e números; não são amostras de produção. O exemplo oficial decimal continua rejeitado. Nenhuma regra de `aliexpress.logistics.buyer.freight.*` foi transferida para DS.

## Diagnóstico ADMIN implementado

O POST existente `/api/admin/integrations/aliexpress/freight-diagnostic` retorna agora `monetaryStructure`, inclusive em rejeições monetárias. A tela ADMIN mostra opção, caminho permitido, presença, tipo JSON, moeda validada, escala documental e valor observado seguro, sem conversão. A opção usa índice, sem IDs externos ou endereço. ADMIN obrigatório e `Cache-Control: no-store` preservados; checkout não recebe essa estrutura.

Somente nomes fixos são emitidos. Objetos e arrays nunca são copiados. Texto livre, mensagens externas, corpo bruto, credenciais, dados de destinatário e sequências numéricas longas são suprimidos. Valores de frete exibíveis são strings decimais com até seis dígitos inteiros e quatro decimais; valores fora do limite ficam omitidos, sem alterar o bloqueio. Moedas exibidas usam lista fechada; moeda fora dela fica omitida. `safeValue` é observação, não preço aprovado. O frontend repete a validação e descarta propriedades adicionais.

Não houve nova conversão de escala. A alteração de segurança rejeita fontes alternativas não documentadas mesmo junto de centavos válidos, em vez de ignorá-las. BigDecimal é usado para toda nova manipulação monetária; nenhum float/double foi introduzido.

## Evidência ainda necessária

1. Executar manualmente o diagnóstico ADMIN no ambiente publicado com o mesmo produto/SKU/quantidade para obter campos e tipos efetivos. Isso fará nova consulta, não recupera a resposta histórica do checkout. Nenhum deploy ou chamada autenticada foi realizado aqui.
2. Obter esclarecimento oficial DS ou contrato corrigido com exemplos consistentes que indiquem se o decimal em shipping_fee_cent representa centavos fracionários ou unidade principal. Comparar visualmente o preço ou assumir por magnitude não comprova escala.
3. Para frete definitivo: comprovar mapeamento dos campos DS de província/cidade para Brasil, cobertura do endereço do pedido e cotação vinculada a itens, quantidades, modalidade e moeda. A consulta atual envia somente país, sem CEP/endereço; o gateway continua COUNTRY_ONLY e o serviço mantém DESTINATION_NOT_CONFIRMED mesmo quando o dinheiro é válido. Não presumir que códigos IBGE são códigos AliExpress.

Nenhuma alteração na importação de produtos ou criação de pedidos. Nenhuma compra, pagamento, commit, push ou deploy.
