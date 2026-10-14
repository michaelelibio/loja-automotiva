# CORS — Railway e Vercel

## Configuração

No serviço backend do Railway que atende `https://api.ingarage.com.br`, configure, sem aspas literais:

```env
FRONTEND_URL=https://ingarage.com.br
CORS_ALLOWED_ORIGINS=https://ingarage.com.br,https://www.ingarage.com.br
```

`FRONTEND_URL` permanece uma URL única para links de verificação, recuperação de senha e e-mails de pedidos. `CORS_ALLOWED_ORIGINS` controla exclusivamente CORS. Na Vercel, mantenha `NEXT_PUBLIC_API_BASE_URL=https://api.ingarage.com.br`; esta correção não exige mudança no frontend.

A propriedade é:

```properties
app.security.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:${FRONTEND_URL:http://localhost:3000}}
```

Quando `CORS_ALLOWED_ORIGINS` não está definida, usamos `FRONTEND_URL`; quando nenhuma está definida, usamos `http://localhost:3000`. No ambiente local, não é necessário adicionar a nova variável. O arquivo `.env.example` é um checklist: não exporte seu campo vazio como uma variável definida. Uma configuração explicitamente vazia ou inválida impede a inicialização, em vez de abrir acesso ou aceitar parcialmente uma lista errada.

O parser separa por vírgula, remove espaços nas extremidades, normaliza uma barra final e elimina duplicatas. Aceita somente origens HTTP/HTTPS com host e porta válida, sem caminhos, query, fragmento, credenciais ou curingas. Entradas vazias, inclusive vírgula final, são rejeitadas com uma mensagem que não reproduz os valores configurados.

As regras de autorização existentes foram preservadas: `OPTIONS /**` e `POST /api/auth/login` continuam públicos, enquanto as demais rotas mantêm sua proteção. A lista de métodos e headers continua incluindo `POST`, `Content-Type` e `Authorization`. Cookies cross-origin não foram habilitados.

## Diagnóstico e divergência com produção

Na análise anterior de 14/10/2026, preflights de ambos os domínios retornaram `403 Invalid CORS request`, sem `Access-Control-Allow-Origin`. A versão local Spring Web 7.0.8 aceita valores separados por vírgula mesmo em `List.of(frontendUrl)`; essa expressão, isoladamente, não explica a rejeição publicada. A separação explícita torna a política independente desse comportamento implícito e evita usar uma lista como URL base de e-mail.

Há apenas um `SecurityFilterChain` e uma configuração CORS em `SecurityConfig`. Não foram encontrados `@CrossOrigin`, `CorsFilter` adicional ou `WebMvcConfigurer` com CORS. O único profile adicional encontrado, `application-local.properties`, habilita o simulador DSers e não altera CORS. Não foram encontrados arquivos Railway, Nixpacks, Dockerfile ou Procfile no projeto que definam outro comportamento de CORS.

Não acessamos as configurações privadas do Railway nem identificamos o código do deployment ativo. Ainda precisam ser conferidos no painel: serviço/ambiente correto, branch e commit publicados, comando de build/start, perfis efetivos, arquivos externos e overrides via `SPRING_APPLICATION_JSON`, `SPRING_CONFIG_LOCATION`, `SPRING_CONFIG_ADDITIONAL_LOCATION`, argumentos `--app.security.cors.allowed-origins=...` ou propriedades JVM. Se a configuração dedicada não estiver definida, um override de `app.security.frontend-url` também afeta o fallback. Evite imprimir conjuntos completos de variáveis, pois contêm secrets.

## Publicação — após autorização

1. Revise os arquivos desta correção junto das alterações pré-existentes. Não publique automaticamente todo o workspace; confirme quais outras alterações precisam fazer parte do artefato.
2. No Railway, selecione o serviço backend e o ambiente de produção ligados a `api.ingarage.com.br`. Confira repositório, branch, diretório de build e comando de inicialização atuais. Defina as duas variáveis acima nesse serviço, preservando as credenciais e variáveis do banco.
3. Após autorização para commit/push/deploy, publique a revisão no branch usado pelo serviço e execute um novo deployment pelo fluxo já configurado. Não basta reiniciar uma imagem antiga: a nova propriedade e o parser precisam estar no artefato publicado.
4. Confira o commit/build do deployment e a inicialização bem-sucedida. Um erro `CORS allowed-origins` indica configuração vazia ou inválida; corrija a variável e publique novamente.
5. Execute os preflights abaixo antes de testar login. Nenhum deles envia senha nem realiza autenticação.

```powershell
foreach ($origin in @('https://ingarage.com.br', 'https://www.ingarage.com.br')) {
    Write-Host "Origin: $origin"
    curl.exe -i --max-time 20 -X OPTIONS 'https://api.ingarage.com.br/api/auth/login' `
        -H "Origin: $origin" `
        -H 'Access-Control-Request-Method: POST' `
        -H 'Access-Control-Request-Headers: content-type'
}
```

Para cada origem, espere HTTP 200, `Access-Control-Allow-Origin` igual à origem enviada, métodos incluindo POST e headers incluindo `content-type`. O servidor devolve uma origem por resposta, não a lista completa.

Confira também o bloqueio de uma origem desconhecida:

```powershell
curl.exe -i --max-time 20 -X OPTIONS 'https://api.ingarage.com.br/api/auth/login' `
    -H 'Origin: https://untrusted.example' `
    -H 'Access-Control-Request-Method: POST' `
    -H 'Access-Control-Request-Headers: content-type'
```

Espere HTTP 403 e ausência de `Access-Control-Allow-Origin`.

6. Abra uma janela privada do navegador e teste login com uma conta de teste válida nos dois domínios. Na aba Network, confira o OPTIONS e o POST: login válido deve retornar 200 e a origem correspondente no header CORS. Senha errada deve produzir o erro de autenticação normal, sem erro CORS. Se WWW redirecionar para o domínio principal, o teste HTTP acima continua verificando WWW separadamente.

Se o preflight continuar retornando 403 após publicar, confirme primeiro o valor efetivo, o artefato e os headers enviados pelo navegador. Não libere `*` nem altere as regras JWT para contornar a divergência. Os testes locais não comprovam que a correção está ativa no Railway.

## Testes

`CorsConfigurationTests` cobre parsing, normalização e rejeição segura de configurações inválidas. `ProductionCorsIntegrationTests` cobre preflight e login real com usuário sintético/H2 nos dois domínios, rejeição de origem desconhecida e manutenção da autenticação em rota privada. `AuthIntegrationTests` mantém os cenários existentes e acrescenta o preflight de login local com fallback.

Validação da suíte: execute `cd backend` e `.\mvnw.cmd clean test`. Para whitespace, execute `git diff --check` na raiz.
