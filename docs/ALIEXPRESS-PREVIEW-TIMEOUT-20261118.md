# AliExpress preview: investigação do HTTP 504 em 18/11/2026

O método real é `POST /api/admin/integrations/aliexpress/preview`. A investigação e as verificações em produção usaram somente a pré-visualização e consultas de leitura. Nenhum produto foi importado pelos diagnósticos.

## Causa comprovada

Os logs HTTP da Railway registraram nove respostas 504 entre 22:07 e 22:14 UTC (19:07–19:14 em São Paulo), com duração de 10.014–10.291 ms. O tempo no upstream correspondia ao tempo total. A aplicação registrava `reason=TIMEOUT`: o limite de conexão do cliente JDK era 10 s, antes do limite de leitura de 20 s. Não era o gateway Railway encerrando uma consulta longa.

O DNS no contêiner direcionava `api-sg.aliexpress.com` para o ingresso Singapura do próprio provedor (`sg.ingress-ae.aliexpress.com`, endereços `47.246.72.*`). Testes de conexão HTTPS no mesmo contêiner expiraram tanto com HTTP/1.1 quanto HTTP/2 e TLS 1.2/1.3. Algumas conexões TCP isoladas também expiraram. Os dados localizam a falha no caminho de conexão Railway → ingresso regional; não permitem atribuir sua causa interna ao provedor ou à rede Railway.

A consulta assinada `aliexpress.ds.product.get` para `1005010598638085` respondeu HTTP 200 / `rsp_code=200` em 1.534 ms fora da Railway, com as mesmas credenciais. Dentro da Railway, a mesma consulta respondeu em 2.019 ms resolvendo o ingresso regional `use.ingress-ae.aliexpress.com`, observado na cadeia CNAME do gateway no Brasil. O hostname/SNI continuou `api-sg.aliexpress.com`, com certificado e hostname verificados pelo Java. O endereço IP foi resolvido dinamicamente.

O item de controle `1005010661577657` também apresentava 504 no endpoint antes da correção. A falha não era exclusiva do produto informado. O domínio legado `api.aliexpress.com` retornou redirecionamento para manutenção e foi descartado.

O access token estava válido até 14/12/2026 21:28:39 UTC e o refresh token até 14/12/2026 02:46:56 UTC. Não houve renovação durante as falhas correlacionadas. A prévia faz uma aquisição do token e uma consulta de produto; não havia consultas de frete ou outras chamadas sequenciais a eliminar.

## Correção e limites

- Transporte separado para a consulta usada pela prévia e pela confirmação manual de importação. A consulta usada pela validação de criação automática continua no transporte JDK anterior.
- Primeira conexão pelo DNS canônico; somente falha de conexão, timeout de conexão/handshake ou falha DNS permite uma tentativa adicional pelo CNAME regional observado. Cada rota usa um endereço resolvido dinamicamente. Não há IP fixo, redirecionamentos nem retries automáticos do cliente.
- Conexão TCP e handshake TLS limitados a 3 s cada. Um orçamento de 20 s é compartilhado pelas duas tentativas e pelo consumo do corpo, com cancelamento da requisição. O tempo restante não é reiniciado no fallback. A resolução DNS nativa do sistema pode bloquear além do prazo; o cancelamento impede enviar HTTP quando essa resolução retorna atrasada. A aquisição/renovação OAuth continua separada e conserva seus limites anteriores.
- HTTP 4xx/5xx, timeout de leitura, resposta inválida e falha de certificado não iniciam a rota alternativa. O CNAME regional é uma dependência operacional observada, não um novo endpoint público documentado; se indisponível, a chamada falha de forma limitada.
- URL, Host, SNI, assinatura, validação de certificado e hostname permanecem canônicos. Nenhum trust manager permissivo foi adicionado.
- Logs seguros registram um identificador de prévia e duração de aquisição/renovação OAuth, DNS, consulta de produto, normalização e total. Tokens, assinaturas, credenciais, payloads e corpos de erro do provedor não são registrados.
- O ADM orienta aguardar e repetir manualmente em caso de 504. Não há repetição automática no frontend.

A dependência Apache HttpClient é gerenciada pelo BOM existente do Spring Boot. Os demais clientes de produção já fixavam a fábrica JDK, exceto Resend; sua fábrica foi explicitada para preservar o transporte anterior, sem alterar envio, autenticação, mensagens ou tratamento de erro. Não há mudanças de schema, configuração de produção, preços, estoque, SKUs, pedidos ou pagamentos.

## Validação

Os testes cobrem assinatura TOP e isolamento da consulta antiga, recuperação única após falha de conexão, handshake TLS real parado, ausência de retry para erros HTTP/leitura/certificado, orçamento restante no fallback, corpo parado após headers, DNS atrasado sem HTTP tardio, prévias concorrentes, logs sem credenciais, renovação OAuth com mock, método POST, falha OAuth antes da consulta e ausência de persistência na prévia.

Validação anterior ao push:

- Backend `mvn verify`: BUILD SUCCESS; 1.069 testes, 951 aprovados e 118 opt-in ignorados, nenhuma falha ou erro; pacote Spring Boot gerado. Fontes da cópia isolada de validação conferidas por hash com a árvore de trabalho.
- Frontend `npm test`: 116 aprovados, nenhuma falha.
- TypeScript `tsc --noEmit --incremental false`: aprovado.
- Frontend `npm run build`: aprovado.
- `npm run lint`: nenhum erro; cinco avisos preexistentes fora dos arquivos alterados.
- `git diff --check`: aprovado.

Referências de implementação: [DNS resolver Apache](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/DnsResolver.html), [cancelamento de requisição Apache](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/classic/methods/HttpUriRequestBase.html).
