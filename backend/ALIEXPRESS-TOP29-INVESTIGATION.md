# TOP_29_INVALID_APP_KEY — gateway e contrato de frete

Investigação de 17/10/2026. Entrega: diagnóstico e proposta técnica; nenhum código de execução ou configuração de produção foi alterado.

## Conclusão

Existe uma divergência comprovada entre o cliente de frete e o contrato/SDK atual: ele chama o gateway Taobao legado, enquanto os exemplos atuais de **ambas** as operações (`freight.calculate` e `ds.product.get`) usam `IopClient` com `Protocol.TOP`. O SDK oficial resolve esse protocolo como `/sync`, HMAC-SHA256, timestamp em milissegundos e token em `session`.

O TOP 29 informado comprova que a identificação da aplicação foi rejeitada **no gateway consultado**. A documentação TOP inclui incompatibilidade de ambiente entre suas causas. Dado que produtos funcionam no gateway AliExpress atual, a hipótese mais consistente é incompatibilidade de gateway/registro de aplicação. A causa exata não pode ser atribuída exclusivamente a isso sem uma consulta autenticada no gateway correto ou confirmação no console. Não há evidência para regenerar App Key, Secret ou token.

## Evidências obtidas

1. [Erros TOP oficiais](https://developer.alibaba.com/docs/doc.htm?articleId=114&docType=1&source=search&treeId=663): o erro 29 também pode resultar de ambiente incompatível; não significa somente valor digitado incorretamente. Não é o erro 11 de permissão de API, nem o 27 de sessão inválida. O 29 não prova em qual ordem o fornecedor avaliaria as permissões da operação.
2. [Referência legada de frete](https://developer.alibaba.com/docs/doc.htm?articleId=39371&docType=2&treeId=762): a árvore aparece como `Dropshipping（已废弃）`, isto é, seção descontinuada, e indica `eco.taobao.com/router/rest`. Essa marcação da seção NÃO prova que o nome da operação deixou de existir: o catálogo atual ainda a publica.
3. [Contrato atual de calculate](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST), docId 525: exemplo Java usa `Protocol.TOP`; `sku_id` consta como String opcional, com observação de envio obrigatório para produto Choice. Isso corrige a conclusão anterior, baseada na referência legada, de que SKU não estava documentada.
4. [Contrato atual de freight.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.get&methodType=GET%2FPOST), docId 1442: título **“Freight calculation (new)”**, autorização exigida e exemplo Java com o mesmo protocolo. Portanto, existe outra API oficial de frete no catálogo Dropshipping atual.
5. [Contrato atual de produtos](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.product.get&methodType=GET%2FPOST), docId 12391: também usa `Protocol.TOP`. O código local e o funcionamento relatado pelo administrador confirmam o uso de `https://api-sg.aliexpress.com/sync` para produtos.

O portal atual foi acessado por HTTP público com `Invoke-WebRequest -UseBasicParsing`, sem login. As tentativas anteriores de leitura não haviam obtido seu conteúdo. Não se deve manter a conclusão anterior de indisponibilidade como evidência de inexistência do método.

O SDK foi baixado do link fornecido nesses contratos, **apenas para leitura do código-fonte**. Não foi executado. Fontes conferidas: `TopExecutor`, `BaseExecutor`, `IopHashMap`, `IopUtils` e `Constants`. `TopExecutor` insere `session` e `/sync`; `BaseExecutor` escolhe `sha256`; `IopHashMap` converte Date para epoch milliseconds; `IopUtils` ordena os parâmetros e usa HMAC-SHA256 com prefixo de API vazio para TOP. Uma cópia reduzida dos metadados públicos e o hash SHA-256 do ZIP estão em `ALIEXPRESS-FREIGHT-PUBLIC-EVIDENCE.json`; não contêm credenciais da aplicação.

## Gateway e protocolo recomendados

| Campo | Frete no código atual | Proposta baseada no SDK atual |
| --- | --- | --- |
| URL | `https://eco.taobao.com/router/rest` | `https://api-sg.aliexpress.com/sync` |
| Verbo/formato | POST form UTF-8 | POST form UTF-8 |
| API | `aliexpress.logistics.buyer.freight.calculate` | Manter inicialmente o mesmo método para isolar a correção |
| Aplicação | `app_key` existente | Mesmo `app_key`, sem alteração |
| Token | `session` existente | Manter `session` com token persistido, sem refresh |
| Assinatura | `hmac` / HMAC-MD5 | `sha256` / HMAC-SHA256 |
| Timestamp | Data GMT+8 formatada | Epoch em milissegundos |
| Assinatura canônica | Nomes/valores ordenados | Nomes/valores ordenados, sem `sign`, sem prefixo de API; hexadecimal maiúsculo |
| Parâmetro de negócio | `param_aeop_freight_calculate_for_buyer_d_t_o` | Manter o objeto JSON com esse nome |
| Versão/retorno | `v=2.0`, `format=json` | Manter |

A recomendação de base `api-sg.aliexpress.com` combina a resolução `/sync` do SDK com o gateway já comprovadamente funcional para a inGarage. Os metadados genéricos do portal também exibem `access_token` entre parâmetros comuns; isso NÃO justifica misturar GOP com TOP: no executor TOP efetivamente indicado pelo exemplo Java, o argumento de token torna-se **`session`**. No GOP, token, prefixo de assinatura e rota são diferentes.

## Parâmetros de negócio confirmados

Para `freight.calculate`, o objeto atual exige `country_code`, `product_id`, `product_num` e `send_goods_country_code`. `sku_id` é String opcional no esquema, com exigência documentada para Choice. `price`, `price_currency`, `province_code` e `city_code` são opcionais. CEP não consta nesse objeto. O caso de teste pode preservar BR, produto e quantidade atuais e incluir a SKU exata `12000053109276547` como String, sem conversão para Number JavaScript. CN permanece hipótese de origem.

Para **`aliexpress.logistics.buyer.freight.get`**, o parâmetro principal muda para **`aeopFreightCalculateForBuyerDTO`**. Campos obrigatórios: `product_id`, `product_num`, `country_code` e `sku_id` (tipo Number no contrato). Origem, preço/moeda e códigos de cidade/província são opcionais. CEP também não está documentado nesse objeto. A existência do campo SKU não garante exatidão por endereço completo nem disponibilidade ao Brasil.

A resposta de `freight.get` não é intercambiável com a atual: usa `aeop_freight_calculate_result_for_buyer_dtolist`, inclui `shipping_method` e o esquema monetário expõe `cent`/`currency`/`currency_code`, sem o mesmo `amount` do método anterior. Não presumir unidades/conversão ou reaproveitar cegamente o parser. A especificação indica booleano para `success`, mas os exemplos atuais serializam `"true"`; arrays diretos também aparecem nos exemplos. Isso deve ser coberto na proposta de adaptação, preservando rejeição de valores conflitantes/incompletos.

Não foi encontrado suporte oficial primário que permita recomendar `aliexpress.ds.freight.query` como substituição. A alternativa comprovada nesta investigação é `aliexpress.logistics.buyer.freight.get`. APIs `alibaba.shipping.freight.calculate`/`alibaba.order.freight.calculate` pertencem a outro produto (ICBU/Alibaba.com), e não são substitutos automaticamente autorizados para AliExpress.

## Permissão da aplicação: o que está e não está provado

| Operação/ambiente | Evidência | Conclusão |
| --- | --- | --- |
| Produtos no AliExpress atual | Consulta funcional relatada | Aceitação comprovada para essa operação |
| Frete no Taobao legado | `TOP_29_INVALID_APP_KEY` relatado | Identificação rejeitada nesse gateway; permissão de frete inconclusiva |
| `freight.calculate` no AliExpress atual | Contrato público e SDK | Compatibilidade técnica documentada; permissão específica da inGarage não comprovada |
| `freight.get` no AliExpress atual | Contrato público exige autorização | Existência comprovada; concessão e funcionamento para a inGarage não comprovados |

Não há acesso autenticado ao console da aplicação nesta investigação. `authType=1` no catálogo significa que a operação exige autorização, não que a nossa aplicação a possui. OAuth e produtos funcionais não conferem automaticamente todas as permissões de frete. O token armazenado/diagnóstico local também não representa uma lista de grants de API.

## Correção proposta e validação real pendente

1. Adaptar somente o diagnóstico para o TOP atual descrito acima, reutilizando os parâmetros documentados, App Key/Secret/token existentes e a leitura sem refresh. Incluir a SKU agora comprovadamente documentada. Não trocar automaticamente para outra operação após um erro.
2. Antes de executar no Railway, testar assinatura SHA-256, URL, epoch, `session`, precisão da SKU, envelopes e formatos documentados de retorno. A aceitação de `success` em String deve ser estrita (`"true"`/`"false"`), nunca conversão booleana permissiva.
3. Executar uma única consulta ADMIN. Uma resposta positiva seria evidência operacional para `calculate`; uma negativa de permissão deve permanecer negativa. Outro código 29 ainda exigiria conferir ambiente e registro da aplicação; não há autorização para modificar suas configurações por tentativa.
4. Confirmar no console os métodos exatos concedidos para a aplicação. Só avaliar migrar para `freight.get` após conferir essa concessão e implementar/adaptar seu contrato próprio, sem fabricar valores monetários.
5. Manter checkout, pedidos e pagamentos bloqueados. Esta proposta não resolve sozinha frete por CEP, validade comercial da cotação, impostos, preço pago ou criação de ordens.

Nenhum cliente HTTP, OAuth, token, configuração, pedido, pagamento ou checkout foi alterado nesta entrega de investigação. Não houve consulta autenticada nova, compra, commit, push ou deploy. Validações desta etapa: conferência estática dos fontes do SDK e do snapshot público, mais `git diff --check`; testes de aplicação não foram reexecutados porque não houve mudança no código de execução.
