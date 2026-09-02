# highscale-api

API Kotlin + Spring Boot 4 reativa, em Clean Architecture. `GET /api/produtos/{id}` usa WebFlux (`suspend`) e R2DBC.

## Stack

- Kotlin coroutines, Java 21, Spring Boot 4.1 (WebFlux + Netty)
- R2DBC PostgreSQL (`DatabaseClient`) para o GET
- JDBC + Flyway só para migrations
- PostgreSQL 16 via Docker Compose
- Actuator + Prometheus (`/actuator/prometheus`)

## Subir localmente

1. Suba o banco:

```powershell
docker compose up -d
```

2. Rode a API empacotada (mais rápido que `spring-boot:run`):

```powershell
.\mvnw.cmd -DskipTests package
java -XX:+UseZGC -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

3. Consulte o produto seed (Caderno):

```powershell
curl http://localhost:8080/api/produtos/11111111-1111-1111-1111-111111111111
```

Seeds:

| id | nome | preco |
| --- | --- | --- |
| `11111111-1111-1111-1111-111111111111` | Caderno | 12.90 |
| `22222222-2222-2222-2222-222222222222` | Caneta | 3.50 |
| `33333333-3333-3333-3333-333333333333` | Lapis | 1.20 |

Health: `http://localhost:8080/actuator/health`

## Teste de 10 mil RPS (k6)

Instale o k6 (`winget install k6`). Com API e Postgres no ar, em **outro terminal**:

```powershell
k6 run load-test/get-produto.js
```

O script sobe a carga num único cenário (um pool de VUs, não a soma de três):

- 10 s: 100 → 2.000 RPS (warmup)
- 15 s: 2.000 → 10.000 RPS (ramp)
- 30 s: 10.000 RPS (platô)

`preAllocatedVUs: 500` (teto 1000). Com mediana < 1 ms bastam dezenas de VUs; 3000 VUs no mesmo notebook brigam com a API por CPU e socket.

Suba a API como jar com ZGC (G1 gera pausas de centenas de ms — o p99 de ~300 ms com mediana de ~0,7 ms é típico disso):

```powershell
java -XX:+UseZGC -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

O GET usa cache Caffeine (mesmo `id` não volta no Postgres depois do warmup). Para medir só o R2DBC:

```powershell
java -XX:+UseZGC -Dapp.cache.produtos.enabled=false -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

**Passa** se, no platô (e no teste como um todo, com o perfil único):

- throughput real ≈ 10.000 RPS no platô de 30 s
- `dropped_iterations` ≈ 0 (se houver drop, a API não sustentou 10k)
- `http_req_failed` < 1%
- p99 < 50 ms (relaxe esse threshold se o alvo for só throughput)

### Como ler gargalos

`dropped_iterations` alto + latência de centenas de ms = a API não acompanha o arrival rate. O k6 esgota os VUs (`vus=maxVUs`) e descarta iterações. Pela lei de Little: `RPS ≈ VUs / latência`. Com 2000 VUs e ~315 ms, o teto é ~6k RPS — aumentar `maxVUs` só piora o p99.

Na stack reativa o teto deixa de ser threads Tomcat. O limite passa a ser o **cache** (hit em memória), o **pool R2DBC**, o event loop do Netty, e a latência do Postgres (no Windows, cada query ainda atravessa o NAT do Docker Desktop).

- **Cache:** `app.cache.produtos.enabled=true` (padrão). Sem cache, o teste vira Postgres-no-Docker, não a API
- **R2DBC:** `spring.r2dbc.pool.max-size=32`. Pool grande demais (100) aumenta contenção no Postgres
- **Postgres/Docker:** `fsync=off` só para bench local; `shm_size=256mb`. CPU do container ou espera por conexão (`max_connections=200`)
- JDBC/Hikari fica pequeno (2 conexões) e só serve o Flyway na subida

## Testes funcionais

```powershell
.\mvnw.cmd test
```

Usa Testcontainers (PostgreSQL 16). O Docker precisa estar rodando.
