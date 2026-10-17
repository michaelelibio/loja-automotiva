# Diagnóstico de autorização AliExpress (somente leitura)

Atualização: foi preparado um POST ADMIN separado para testar uma consulta pontual de frete com o token existente, sem refresh. Consulte [ALIEXPRESS-FREIGHT-DIAGNOSTIC.md](ALIEXPRESS-FREIGHT-DIAGNOSTIC.md). O GET descrito aqui permanece somente local e não passa a afirmar permissão de frete; nenhuma consulta com a autorização Railway foi executada durante a implementação.

## Resultado desta inspeção

Não foi possível confirmar um Access Token da instalação publicada: este processo não tem as variáveis AliExpress/banco carregadas e não foi encontrado backend local na porta 8080. Existe `.env.local.ps1`, mas a análise estática detectou erros de sintaxe; o arquivo não foi executado nem modificado e nenhum conteúdo sensível foi exibido. Isso não comprova ausência de autorização no servidor publicado.

O token existente fica criptografado em `aliexpress_authorizations`, com expiração e vínculo à aplicação. `AliExpressProductService` obtém o token de `AliExpressTokenService` e o fornece ao cliente HTTP de produtos. O método `accessToken()` pode renovar e persistir credenciais automaticamente: ele NÃO é utilizado pelo novo diagnóstico.

## Consulta segura na instalação que contém a autorização

`GET /api/admin/integrations/aliexpress/authorization-diagnostic`, usando a autenticação ADMIN normal da inGarage. O endpoint precisa estar presente na versão executada; nenhuma publicação foi realizada nesta tarefa. Resposta com `Cache-Control: no-store`.

O resultado contém apenas:

- `applicationConfigured`: presença de App Key/Secret sem valores.
- `accessTokenState`: `APPLICATION_NOT_CONFIGURED`, `AUTHORIZATION_NOT_FOUND`, `STORAGE_UNAVAILABLE`, `EXPIRED_OR_EXPIRING`, `UNREADABLE` ou `LOCALLY_USABLE`.
- `accessExpiresAt`: expiração registrada, sem token.
- `providerValidation` e `freightPermission`: `NOT_VERIFIED` enquanto não houver evidência de uma chamada real.
- `refreshAttempted`: sempre `false`.

`LOCALLY_USABLE` significa que o token pode ser descriptografado e tem mais de 30 segundos restantes na expiração registrada. Não comprova que não foi revogado nem que a API de frete está habilitada. O diagnóstico consulta por ID sem bloqueio de escrita, não altera registros, não renova tokens e não expõe mensagens de exceções ou causas internas. Não retorna App Key, Secret, tokens, refresh token, texto cifrado, IDs da autorização ou dados pessoais.

## Compartilhamento com o cliente de frete

Sim, a infraestrutura OAuth, armazenamento criptografado e seleção da autorização por aplicação podem ser compartilhados. Não é necessário criar outro OAuth ou duplicar credenciais. Um futuro cliente de frete deve receber o token no servidor, como o cliente de produtos.

Não reutilizar cegamente o transporte e a assinatura do cliente de produtos: ele usa TOP `/sync`, SHA-256 e timestamp em milissegundos. A referência pública de `freight.calculate` consultada documenta TOP `/router/rest`, `hmac`/`md5`, timestamp GMT+8 e autorização `session`. É necessário confirmar o transporte realmente habilitado para o app antes de implementar uma chamada. Nenhuma assinatura ou credencial foi alterada nesta tarefa.

Referência oficial: https://developer.alibaba.com/doc2/apiDetail.htm?apiId=39371 . A API exige autorização e dados obrigatórios do produto, quantidade, destino e país de origem. O funcionamento de `aliexpress.ds.product.get` não comprova permissão de `aliexpress.logistics.buyer.freight.calculate`.

## Como concluir a verificação de permissão

1. Consultar o diagnóstico no ambiente que contém a autorização, sem compartilhar credenciais. Se estiver expirado, este fluxo para; não faz refresh automático.
2. Conferir no console AliExpress da aplicação a concessão do método exato e seu contrato/transporte. Um token presente não substitui essa concessão.
3. Com contrato e origem reais confirmados, executar apenas uma consulta de frete, sem criar pedido. O adaptador deve distinguir sucesso, negativa explícita de permissão, token inválido/expirado e resultado inconclusivo. Erros de parâmetro, indisponibilidade, timeout ou assinatura inválida não comprovam falta de permissão.
4. Guardar somente evidência sanitizada: método, instante, resultado categorizado e código permitido; nunca corpo completo, parâmetros de autenticação, CPF, telefone ou endereço.

Não foi criada chamada de teste com parâmetros inventados. A cotação no checkout, pagamentos AliExpress e criação real de ordens continuam bloqueados conforme a etapa anterior. Links de pagamento históricos não são revogados automaticamente.

## Arquivos desta tarefa

- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressAuthorizationDiagnostics.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressDiagnosticsController.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressAuthorizationDiagnosticsTests.java`
- `src/test/java/com/garage/garageapi/admin/AdminAuthorizationIntegrationTests.java`
- Este documento.

Sem migração de banco, alteração/regeneração de credenciais, compra, habilitação de pagamentos, commit, push ou deploy.
