# Diagnóstico de frete pelo ADM — 19/10/2026

Atualização de 20/10/2026: o diagnóstico migrou para `aliexpress.ds.freight.query` e deixou de exigir origem na entrada. Este texto descreve a versão anterior; consulte [o procedimento atual e suas limitações monetárias](ALIEXPRESS-DS-FREIGHT-DIAGNOSTIC.md).

## Protocolo e evidências

O diagnóstico usa uma única URL: `https://api-sg.aliexpress.com/sync`, a mesma base da integração funcional de produtos. Método mantido: `aliexpress.logistics.buyer.freight.calculate`. POST form UTF-8, `session`, `sign_method=sha256`, HMAC-SHA256 sobre nomes/valores ordenados, sem prefixo de API, hexadecimal maiúsculo e timestamp epoch em milissegundos. Não há fallback de gateway nem de método.

O [contrato público atual](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST) foi consultado novamente em 19/10/2026: docId 525, authType 1, exemplo Java com `Protocol.TOP`. Seu link de SDK foi baixado exclusivamente para leitura, sem executar o SDK:

- URL: `https://ae-open-platform-public.oss-ap-southeast-1.aliyuncs.com/online/oss_1792389728386_0_ZUZJ.zip`.
- SHA-256 do ZIP: `3EE8163BD8BAC2F2EF49DF2C1E496DEB6B42A3FCCD8A4895DE5C72D22B40BCE0`.
- `TopExecutor`: rota `/sync`, parâmetro `session`, assinatura com prefixo vazio.
- `BaseExecutor`: padrão `sha256`; `IopHashMap`: Date convertido para epoch milliseconds; `IopUtils` e `Constants`: HMAC-SHA256 e ordenação dos parâmetros.

Os metadados comuns genéricos do portal mencionam `access_token` e timestamp formatado para TOP. A implementação segue o executor TOP indicado no exemplo da própria operação, conferido acima, e a integração funcional de produtos. Não combina campos de protocolos diferentes.

A referência Taobao legada usa `eco.taobao.com/router/rest` e HMAC-MD5. O TOP 29 anterior indica rejeição da identificação da aplicação **nesse ambiente**; não comprova token inválido nem ausência de permissão da operação. A comparação histórica e as evidências anteriores estão em `ALIEXPRESS-TOP29-INVESTIGATION.md`, preservado nesta tarefa.

## Compatibilidade e permissão

O método consta no catálogo Dropshipping consultado (`cid=21038`) e exige autorização. Isso comprova disponibilidade documental e compatibilidade técnica do protocolo, sem comprovar a concessão do método para a aplicação inGarage. Não houve acesso autenticado ao console nem consulta com o OAuth do Railway nesta tarefa. A permissão permanece inconclusiva até uma chamada autenticada ou conferência dos grants no console. Sucesso na API de produtos não demonstra permissão na API de frete.

Uma falha na leitura prévia do produto retorna `PRODUCT_LOOKUP_FAILED`: seu status se refere a produtos, sem atribuir esse erro à API de frete. Os códigos TOP conhecidos continuam separados de erros de negócio. Assinatura (24/25), identificação/gateway (28/29), método (21/22), token, permissão, parâmetros, comunicação e resposta inválida têm códigos seguros distintos; mensagens brutas não são encaminhadas.

## Operação em /admin/produtos

1. Escolher um AliExpress importado entre os produtos da página atual, ou informar o ID externo.
2. Usar **Consultar SKUs e origem**. O backend consulta `ds.product.get` com destino BR, moeda BRL e idioma inglês, usando apenas o token persistido, sem refresh.
3. Selecionar SKU e quantidade (1–100, limitada ao estoque retornado).
4. Usar **Testar cotação de frete**. O backend relê o produto e confere SKU, estoque e origem antes de enviar uma única chamada de frete.

O navegador envia somente ID do produto, SKU como String e quantidade. Não decide origem, preço ou moeda. O backend envia os campos obrigatórios do DTO e a SKU documentada; omite preço e moeda opcionais para não misturar preço comercial local com preço do fornecedor. IDs de SKU não passam por Number JavaScript.

A origem vem exclusivamente de um atributo do SKU cujo nome retornado é `Ships From`, com valor ISO alpha-2 ou nome inglês de país reconhecido pelo Java. Não se deduz origem de país da loja, destino, SKU, URL ou preço. Valores ausentes, ambíguos ou não reconhecidos bloqueiam a cotação (`SHIPPING_ORIGIN_UNCONFIRMED`). Isso pode bloquear produtos reais sem esse atributo; não há entrada manual para contornar o bloqueio. CN não é padrão.

Os endpoints são ADMIN e retornam `Cache-Control: no-store`:

- `POST /api/admin/integrations/aliexpress/freight-diagnostic/product` com `{"productId":"..."}`.
- `POST /api/admin/integrations/aliexpress/freight-diagnostic` com `{"productId":"...","skuId":"...","quantity":1}`.

O painel mostra status, código seguro, modalidades, moedas, valores e estimativas retornados. O parser aceita booleano estrito ou strings `"true"`/`"false"`, lista direta ou envelope documentado; rejeita valores monetários ausentes/conflitantes. Não converte moeda nem interpreta ausência de modalidades como frete grátis. Não solicita endereço nem dados pessoais.

O diagnóstico não informa frete por CEP, garantia de prazo, impostos ou validade comercial. Não altera checkout, produtos, pedidos ou pagamentos; a compra de AliExpress continua bloqueada pelas proteções existentes. Não houve commit, push ou deploy.

## Validação

Testes cobrem transporte/assinatura, formatos de resposta, sanitização, proteção ADMIN, token sem refresh, seleção e revalidação de SKU, origem ausente/ambígua e estoque insuficiente. A regressão inclui as proteções existentes de checkout e pedidos.

- Backend: suíte completa com 533 testes, mais dois casos adicionados de proteção/sanitização da preparação e reexecução de `AliExpressFreightDiagnostic*Tests`. Relatórios Surefire finais: 535 testes, zero falhas, erros ou ignorados.
- Frontend: 39 testes aprovados; `tsc --noEmit` e ESLint dos arquivos alterados aprovados.
- `git diff --check`: aprovado.
- Validação real no Railway pendente: não houve consulta autenticada nem publicação automática.
