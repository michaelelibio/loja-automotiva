# AliExpress OAuth local

## Contrato consultado

Consultado em 12/10/2026 no portal oficial: [Authorize your APP, docId=1590](https://openservice.aliexpress.com/doc/doc.htm#/?docId=1590). O conteúdo foi obtido também pelo [endpoint público da documentação](https://openservice.aliexpress.com/handler/share/doc/getDocDetail.json?docId=1590). O guia atual descreve autorização da conta AE e exemplifica `account_platform=buyerApp`, aplicável ao fluxo de autorização da aplicação Drop Shipping. Ele exige HTTPS POST para a troca do código.

Usamos `POST https://api-sg.aliexpress.com/rest/auth/token/create`, conforme a base REST do exemplo do guia. O exemplo Java usa um cliente GOP que encapsula a URL; nossa implementação monta a requisição REST diretamente. Não usamos o fluxo antigo TOP `/router/rest`, `grant_type=authorization_code`, Basic Auth ou `/auth/token/security/create`.

O formulário UTF-8 contém `app_key`, `code`, `timestamp` (epoch em milissegundos), `sign_method=sha256` e `sign`. `ALIEXPRESS_CALLBACK_URL` é a configuração do redirect da autorização, não um parâmetro adicional da troca. O App Secret fica apenas como chave da assinatura.

Assinatura: ordenar os parâmetros por nome ASCII, concatenar nomes e valores sem separadores, prefixar `/auth/token/create`, calcular HMAC-SHA256 com App Secret sobre UTF-8 e converter para hexadecimal maiúsculo. Não incluir `sign` no cálculo. Fontes oficiais: [assinatura](https://developer.alibaba.com/docs/doc.htm?articleId=120692&docType=1&treeId=727) e [parâmetros](https://developer.alibaba.com/docs/doc.htm?articleId=120691&docType=1&treeId=727). A documentação de assinatura de 2022 é usada para o transporte REST descrito pelo guia OAuth de 2024, ainda publicado no portal; não foi identificado contrato conflitante nesses documentos.

O JSON de transporte é o corpo GOP direto: `code=0` indica sucesso. O wrapper `gopResponseBody` mostrado no guia é a representação do SDK e não é um envelope adicional enviado por este cliente REST. Mapeamos `access_token`, `refresh_token`, `expires_in`, `refresh_expires_in`, `expire_time`, `refresh_token_valid_time`, `user_id` e `account_id` quando disponíveis. Durações são em segundos; timestamps absolutos, em milissegundos. Campos numéricos podem vir como número ou string. Identificadores podem estar ausentes.

Erros GOP podem trazer `code`, `message`, `type` e `request_id`. Qualquer `code` diferente de zero, erro HTTP/transporte, JSON inválido ou ausência do access token é falha. O serviço não transmite mensagens, corpos ou causas da API externa para logs/respostas. Não depende de uma lista fixa de códigos de erro.

## Comportamento

- Callback público existente: `GET /api/integrations/aliexpress/callback`.
- `error` presente: HTTP 400 e nenhuma troca, mesmo se houver `code`.
- Código ausente ou em branco: HTTP 400 e nenhuma troca.
- Troca bem-sucedida: HTTP 200, `Autorizacao AliExpress concluida com sucesso.`.
- Falha de configuração ou de troca: HTTP 502 com mensagem genérica para autorizar novamente.
- Logs: presença/tamanho dos parâmetros, sucesso/falha, presença dos tokens e expirações. Valores brutos, identificadores, assinatura e tokens não são registrados.
- Tokens retornam apenas ao serviço chamador e são descartados pelo controller; não são salvos, exibidos ou utilizados em outras APIs.

## Teste manual — somente OAuth

1. Confira manualmente as três variáveis já existentes em `backend/.env.local.ps1`: `ALIEXPRESS_APP_KEY`, `ALIEXPRESS_APP_SECRET` e `ALIEXPRESS_CALLBACK_URL`. O arquivo privado não foi alterado por esta etapa.
2. O callback no console do app AliExpress, na variável e na URL de autorização deve ser exatamente `https://costumes-engage-involving-witch.trycloudflare.com/api/integrations/aliexpress/callback`. Se o túnel temporário mudar, atualize esses três locais manualmente.
3. Reinicie o backend com `cd backend` e `.\start-local.ps1`. Mantenha seu Cloudflare Tunnel apontando para `http://localhost:8080`.
4. Uma chamada sem código a `http://localhost:8080/api/integrations/aliexpress/callback` deve responder 400 com mensagem de callback incompleto, sem exigir JWT. Uma chamada com `?error=access_denied` deve responder 400 sem trocar token.
5. Em um PowerShell separado, carregue a configuração local e monte a URL de autorização sem expor App Secret:

```powershell
. .\backend\.env.local.ps1
$redirect = [Uri]::EscapeDataString($env:ALIEXPRESS_CALLBACK_URL)
$appKey = [Uri]::EscapeDataString($env:ALIEXPRESS_APP_KEY)
$authorizeUrl = "https://api-sg.aliexpress.com/oauth/authorize?response_type=code&force_auth=true&redirect_uri=$redirect&client_id=$appKey"
$authorizeUrl
```

6. Copie a URL para o navegador, entre na conta AE apropriada e autorize o app. O AliExpress redireciona para o túnel com um código; o backend troca-o por tokens automaticamente. Não cole códigos ou tokens no chat, nem recarregue/reutilize o callback com o mesmo código.
7. Confira HTTP 200 e a mensagem de sucesso. No backend, procure `AliExpress token exchange succeeded` e as informações de expiração. Em caso de 502, confira credenciais, callback registrado, permissões/status do app e conectividade; faça nova autorização para obter outro código.

O teste real não foi executado automaticamente. Não há importação, pedido, pagamento, fulfillment, tracking, refresh automático ou persistência nesta etapa. O parâmetro `state` é aceito e omitido dos logs, mas não é validado: ainda não existe um fluxo de início de autorização com state gerado e vinculado à sessão. Este callback destina-se à validação manual local; proteção completa de state e gerenciamento durável dos tokens ficam pendentes.

## Testes automatizados

`AliExpressAuthServiceTests` usa transporte HTTP simulado, sem chamadas externas, para validar assinatura, método, URL, encoding, mapeamento e falhas seguras. `AliExpressCallbackIntegrationTests` usa serviço mockado e a configuração real de Spring Security/H2 para validar callback, erros e proteção de outras rotas.
