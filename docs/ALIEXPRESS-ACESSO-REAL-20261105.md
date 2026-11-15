# AliExpress: verificação de acesso real — 05/11/2026

## Resultado e limites

Investigação de leitura, sem executar `aliexpress.trade.buy.placeorder`, refresh de OAuth, compra, pagamento, migração ou alteração de produção. Nenhum código de execução foi alterado nesta investigação. As alterações de implementação já existentes no diretório foram preservadas.

O usuário confirmou que a aplicação inGarage está Online e o grupo AliExpress-dropship está Active. A documentação oficial define Active como grupo disponível/concedido à aplicação e permite abrir o grupo para conferir os métodos integrantes. Portanto, há um caminho administrativo para comprovar a concessão sem realizar uma compra: conferir os dois métodos dentro do grupo Active da aplicação cujo App Key corresponde ao configurado no backend. A presença no catálogo geral, isoladamente, não estabelece essa associação.

Fonte: [Request API Permission](https://developer.alibaba.com/docs/doc.htm?articleId=120676&docType=1&treeId=727).

### Confirmação posterior do proprietário

O usuário confirmou expressamente que `aliexpress.trade.buy.placeorder` e `aliexpress.trade.ds.order.get` aparecem dentro do grupo Active da aplicação inGarage, e não apenas na documentação geral. Isso constitui evidência administrativa informada pelo proprietário da concessão desses métodos à aplicação. Não há motivo, com as evidências atuais, para afirmar que falta permissão de placeorder. A correspondência do App Key dessa aplicação com o configurado no backend ainda deve ser conferida privadamente; nenhuma execução de placeorder foi feita para testar aceitação operacional.

O usuário informou não possuir um ID de pedido AliExpress existente para consulta. Portanto, essa via de verificação da conta compradora não está disponível agora; não usar o número interno #19, IDs fictícios ou pedidos de terceiros como substitutos. A alternativa continua sendo conferir a identidade/vínculo da autorização OAuth na origem privada ou no console, se essa informação estiver disponível, sem criar pedidos nem renovar credenciais nesta tarefa.

Esta confirmação atualiza as referências a pendência de lista de métodos nas seções abaixo: a lista foi confirmada pelo usuário; permanecem a conferência de correspondência do App Key, a identidade OAuth e as demais pendências técnicas/comerciais.

Não foi identificada nesta investigação uma operação oficial documentada de introspecção de permissões que comprove placeorder via chamada somente de leitura. Não enviar placeorder com payload inválido como teste de permissão: continua sendo um método de escrita, e uma validação de payload não comprovaria todos os controles posteriores.

## Comparação do contrato

| Operação | Contrato oficial consultado | Implementação local |
| --- | --- | --- |
| OAuth | Família de sistema `/rest/auth/token/create`, assinatura HMAC-SHA256 com caminho da API prefixado à sequência ordenada de parâmetros | `AliExpressAuthService` usa `/rest`, caminho `/auth/token/create` ou `/auth/token/refresh`, epoch em milissegundos, SHA256 e hexadecimal maiúsculo; não foi executada troca/renovação nesta investigação |
| Criar pedido | `aliexpress.trade.buy.placeorder`, protocolo TOP, parâmetro `param_place_order_request4_open_api_d_t_o` | `AliExpressOrderClient` usa `https://api-sg.aliexpress.com/sync`, `session`, parâmetros ordenados sem prefixo de caminho, HMAC-SHA256, epoch em milissegundos e formulário UTF-8; corresponde ao transporte do SDK oficial consultado, sem comprovar aceitação comercial do payload |
| Consultar pedido indicado pelo usuário | `aliexpress.trade.ds.order.get`, TOP, `single_order_query` contendo `order_id`; envelope `aliexpress_trade_ds_order_get_response` | O cliente local NÃO usa esse contrato |
| Consultar pedido usado no código | `aliexpress.ds.trade.order.get`, TOP, `order_id` escalar; envelope `aliexpress_ds_trade_order_get_response` | `AliExpressOrderQueryClient.METHOD` aponta para este outro método; a permissão dele precisa ser conferida separadamente |

Os dois métodos de consulta existem na documentação consultada. Não são intercambiáveis apenas alterando o nome: entrada, envelope e interpretação de resposta diferem. Se o grupo conceder somente `aliexpress.trade.ds.order.get`, será necessário adequar e testar o cliente de consulta antes de habilitar criação real com acompanhamento pelo ADM. Nenhuma adequação foi realizada nesta investigação.

As versões antigas da documentação TOP descrevem gateway e algoritmos antigos. A comparação acima usa o exemplo `Protocol.TOP` das definições atuais e o SDK oficial associado, em vez de misturar contratos de épocas diferentes. O checkout OAuth do comprador não deve ser confundido com a autenticação de APIs de negócio: sistema assina com o caminho; TOP assina sem esse prefixo.

Fontes oficiais das definições atuais:

- [placeorder — docId 479](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.buy.placeorder&methodType=GET%2FPOST)
- [trade.ds.order.get — docId 524](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.ds.order.get&methodType=GET%2FPOST)
- [ds.trade.order.get — docId 1199](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.trade.order.get&methodType=GET%2FPOST)
- SDK público associado às definições: SHA-256 `E36C92A8E6583E560CD100BE4EF23FFF30674B52E6CE1ACFDCFAE928055BB889`.

## Consulta autenticada executada

Em **05/11/2026 às 21:50:20 -03:00** (`2026-11-06T00:50:20.589Z`), foi executado exclusivamente `aliexpress.ds.product.get`, para o produto do pedido próprio #19 já autorizado. Configuração e token existente foram lidos em memória; a consulta ao banco usou transação READ ONLY. Não foram impressos credenciais, token ou dados pessoais. Não houve renovação de token ou repetição automática.

Resultado sanitizado:

- App Key, segredo, assinatura, gateway e token existente foram aceitos para essa consulta.
- Produto `1005010687850279`: `onSelling`.
- SKU `12000053193152987`: encontrado; `sku_attr=14:193`; estoque informado: 118.
- Preço regular retornado: BRL 145,56; oferta retornada: BRL 62,59.
- Frete, impostos, total final, elegibilidade promocional e modalidade de entrega: não comprovados por essa consulta. Esses valores não constituem orçamento final aprovado.
- Evidência sanitizada local: `backend/target/aliexpress-access-audit/product-readonly-result.json` (diretório de build ignorado pelo Git).

Essa resposta NÃO comprova permissão de placeorder nem identidade da conta compradora. Não foi consultado pedido AliExpress: ainda falta um ID existente autorizado dessa conta. O número interno #19 não é um ID de pedido do fornecedor.

## Identidade OAuth

O registro de autorização é selecionado pelo hash do App Key e os tokens são cifrados com contexto associado a esse registro. Isso associa a credencial à configuração da aplicação, mas não identifica o comprador.

`AliExpressAuthService` extrai `user_id` e `account_id` quando presentes; `AliExpressTokenService.update` persiste somente os tokens cifrados e suas expirações. Não preserva esses identificadores. Portanto, os dados persistidos atuais não bastam para afirmar que o token pertence à conta compradora pretendida.

Para fechar a verificação sem escrita: conferir o vínculo de autorização no console/origem privada do OAuth, caso disponível, ou consultar um pedido AliExpress existente autorizado e comparar o resultado com a conta compradora acessada pelo proprietário. Ausência de resultado, erro de consulta ou sucesso em produto não provariam a titularidade. Não foi inventado endpoint de introspecção nem renovado o token para tentar recuperar identidade.

O acesso visual ao console foi encerrado pela ferramenta por não conseguir confirmar a URL do navegador; não houve nova tentativa de interação visual. Assim, o estado Online/Active é informação confirmada pelo usuário, e não inspeção independente do console nesta investigação.

## O que falta para uma única criação real não paga do #19

1. Conferir App Key da aplicação e os nomes exatos dos métodos dentro do grupo Active. Registrar evidência privada da concessão, sem expor segredo ou token.
2. Confirmar a conta compradora vinculada ao OAuth pelos meios de leitura acima. A propriedade do pedido #19 e autorização do endereço já foram confirmadas pelo usuário; isso não identifica o titular do token.
3. Resolver qual método de consulta está concedido e assegurar que o adaptador correspondente consulta os estados reais e mantém IDs de fornecedor sem perda de precisão.
4. Validar modalidade de entrega disponível para o endereço autorizado, frete, impostos, custos e total atual. O produto/SKU e o estoque tiveram leitura positiva; a oferta não é confirmação do total da compra.
5. Preparar e revisar a configuração exclusiva do #19 e o payload final, incluindo dados brasileiros exigidos pela definição atual (como CPF e aniversário em `ddMMyyyy`), sem publicá-los no repositório ou no chat. Na inspeção de leitura anterior a configuração de teste do #19 ainda não estava pronta.
6. Tratar os bloqueios locais de forma explícita em uma futura alteração revisada: `AliExpressPlaceOrderPermission.verified()` retorna sempre `false`; `AliExpressTestReadiness.blockers()` adiciona impedimentos incondicionais. Isso é bloqueio de software, NÃO resposta de negação do AliExpress. Alterar somente variáveis não habilita a operação atual. Não enfraquecer a exigência de pagamento real/live_mode dos pedidos normais nem usar pagamento simulado como autorização de compra.
7. Antes de qualquer execução futura, revisar o payload e confirmar administrativamente uma única criação real com chave idempotente persistida, preservando bloqueio após timeout/ambiguidade até reconciliação. Não há autorização para executar nesta tarefa. A criação não inclui pagamento automático; é necessário conferir o estado real retornado/consultado e a aparição em Aguardando pagamento, sem presumir esse estado antes da confirmação do fornecedor.

Não se concluiu que falta permissão da plataforma. Concluiu-se que a concessão exata ainda precisa ser associada à aplicação configurada, a identidade do comprador não está comprovada pelos dados persistidos e há pendências de contrato de consulta, configuração comercial e bloqueios locais. Não houve sucesso simulado de criação.

## Validação desta investigação

Não houve mudança de código executável nem de produção; não foi necessário repetir builds e testes de implementação por uma investigação documental e uma chamada de leitura. Os resultados de testes da preparação anterior permanecem documentados em `ALIEXPRESS-REAL-ORDER19-20261105.md` e não foram apresentados como nova execução nesta tarefa.
