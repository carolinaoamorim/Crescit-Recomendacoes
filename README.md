# Acessor de Investimentos - Recomendacoes

Servico Spring Boot responsavel por manter ativos disponiveis e gerar recomendacoes
compativeis com o perfil do investidor.

## Principais endpoints

```text
GET  /assets
GET  /assets/{ticker}
POST /assets
PUT  /assets/{ticker}

POST /recommendations/{userId}/generate
GET  /recommendations/{userId}
```

## Configuracao

### Auth0 compartilhado

O backend aceita access tokens JWT assinados com RS256. A assinatura, o issuer,
o audience, a validade e o subject (`sub`) sao validados antes de acessar as rotas.
O usuario vem do `sub`; um `userId` diferente na URL retorna 403, inclusive para ADMIN.

| Endpoint | Permissao no claim `permissions` | Regra adicional |
| --- | --- | --- |
| `GET /assets` e `GET /assets/{ticker}` | `read:assets` | Usuario autenticado |
| `POST /assets` e `PUT /assets/{ticker}` | `write:assets` | Permissao reservada ao ADMIN no Auth0 |
| `GET /recommendations/{userId}` | `read:recommendations` | `userId == sub` |
| `POST /recommendations/{userId}/generate` | `write:recommendations` | `userId == sub` |
| `GET /hello` | Nenhuma | Usuario autenticado |
| `GET /health` | Nenhuma | Publico; confirma apenas que a API esta respondendo |

Configure na API compartilhada do Auth0:

1. Habilite **Enable RBAC** e **Add Permissions in the Access Token**.
2. Associe as permissoes de leitura/geracao ao USER. Reserve `write:assets` ao ADMIN.

A API autoriza pelas permissoes que o Auth0 emite no token. Conforme o contrato
do projeto, `write:assets` deve ser atribuida apenas a role ADMIN no painel;
o backend exige essa permissao para criar e alterar ativos. Nao e necessario
incluir nomes de roles em um claim customizado ou criar uma Action para este backend.
Uma role chamada ADMIN sem a permissao `write:assets` nao autoriza a escrita.

O frontend deve pedir o audience compartilhado e enviar
`Authorization: Bearer <access_token>`. O backend nao precisa de Client ID ou Client Secret.
As permissoes sao lidas de `permissions`; o campo `scope` nao substitui essas permissoes.

### Variaveis de ambiente

O arquivo `.env.example` contem todos os nomes usados pela aplicacao:

```properties
AUTH0_ISSUER=https://dev-s41b58hqkvvmdu13.us.auth0.com/
AUTH0_AUDIENCE=https://api.acessor-investimentos
FRONTEND_URL=http://localhost:5173
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=5432
DB_NAME=recomendacoesdb
DB_USER=postgres
DB_PASSWORD=postgres
PERFIL_SERVICE_URL=http://localhost:8081
```

Mantenha a barra final em `AUTH0_ISSUER`. O CORS aceita somente a origem
definida em `FRONTEND_URL`, com os headers `Authorization` e `Content-Type`.
Nao ha sessao de login no backend; cada requisicao deve trazer seu Bearer token.

Para rodar com Java 17 ou superior e Maven, inicie o PostgreSQL e execute:

```bash
mvn spring-boot:run
```

Os valores acima sao os padroes locais. Para substitui-los no Maven, exporte as
variaveis no terminal (por exemplo, `$env:SERVER_PORT="8083"` no PowerShell).
O Spring nao carrega `.env` automaticamente.

Ao gerar recomendacoes, o servico encaminha o Bearer token para Perfil.
O token precisa tambem de `read:profile`, e `PERFIL_SERVICE_URL` deve apontar
para o servico de Perfil que valida o mesmo issuer/audience.

Para verificar o bloqueio sem token:

```bash
curl -i http://localhost:8080/assets
# HTTP 401
```

Com um access token em uma variavel de ambiente, no PowerShell:

```powershell
curl.exe -i http://localhost:8080/assets -H "Authorization: Bearer $env:ACCESS_TOKEN"
```

Um token valido sem a permissao necessaria recebe 403. Com `read:assets`, recebe 200.
Para escrever ativos, e exigida `write:assets`, que deve ser reservada ao ADMIN no Auth0.

## Docker

O Dockerfile compila e executa com Java 17, conforme o `pom.xml`.
Para rodar a imagem com um PostgreSQL ja acessivel:

```bash
docker build -t recomendacoes .
docker run --rm --env-file .env --add-host=host.docker.internal:host-gateway -p 8083:8080 recomendacoes
```

Antes, copie `.env.example` para `.env` e ajuste os enderecos do banco e de Perfil.
Se estiverem rodando no computador host, use `DB_HOST=host.docker.internal` e
`PERFIL_SERVICE_URL=http://host.docker.internal:8081`.
Dentro do container, `localhost` aponta para o proprio container.
Mantenha `SERVER_PORT=8080` no container; o exemplo publica a API na porta 8083 do host.

## Deploy

O workflow valida testes e cobertura em pull requests e pushes para `main`.
Publicar a imagem e executar o deploy continuam restritos a push na `main`.
O container recebe estas GitHub repository variables, com os padroes compartilhados:

- `AUTH0_ISSUER`
- `AUTH0_AUDIENCE`
- `FRONTEND_URL`

Os secrets existentes `DOCKERHUB_TOKEN`, `HOST_TEST` e `KEY_TEST` continuam necessarios.
A porta externa do deploy foi mantida em 8081, como no workflow original;
o container usa a porta 8080. Para a integracao futura, ajuste os enderecos dos
servicos e `FRONTEND_URL` ao ambiente de destino.

## Testes

```bash
mvn verify
```

O relatorio JaCoCo e gerado em `tests/`, seguindo o mesmo padrao usado no
repositorio de pagamentos. O build exige pelo menos 80% de cobertura de
instrucoes no projeto e em cada classe do pacote `service`.

Os testes de seguranca usam tokens RSA assinados e um endpoint JWKS local,
exercitando o decoder real sem depender de login ou acesso ao Auth0.
Cobrem 401, 403, permissoes, escrita sem dependencia de claim de roles, propriedade do usuario, CORS, expiracao,
issuer/audience incorretos, assinatura invalida e encaminhamento do token a Perfil.
Os testes existentes com PostgreSQL usam Testcontainers e sao ignorados
automaticamente quando Docker nao esta disponivel.

Referencias: [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
e [RBAC e permissoes no access token do Auth0](https://auth0.com/docs/get-started/apis/enable-role-based-access-control-for-apis).
