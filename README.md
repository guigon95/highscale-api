# highscale-api

API Kotlin + Spring Boot 4 reativa, em **Clean Architecture**. O objetivo do projeto é servir um `GET /api/produtos/{id}` de alta taxa (cerca de 10 mil RPS numa instância) com WebFlux/Netty, R2DBC e cache Caffeine.

Fluxo: controller (adapter inbound) → use case → repositório (porta). A persistência é R2DBC (`DatabaseClient`); o cache Caffeine envolve o repositório. JDBC + Flyway entram só na subida, para migrations.

## Stack

- Kotlin coroutines, Java 21, Spring Boot 4.1 (WebFlux + Netty)
- `GET /api/produtos/{id}` com `suspend` + R2DBC (`DatabaseClient`)
- JDBC + Flyway apenas para migrations (Hikari: 2 conexões)
- PostgreSQL 16 via Docker Compose
- Actuator + Prometheus (`/actuator/health`, `/actuator/prometheus`)
- Cache Caffeine no GET por id (`app.cache.produtos.enabled=true` por padrão; máximo 10.000 entradas, TTL 5 min)
- Pool R2DBC: `initial-size=16`, `max-size=32`, `preparedStatementCacheQueries=256`

## Subir localmente

1. Suba o banco:

```powershell
docker compose up -d
```

2. Empacote e rode o jar (mais rápido que `spring-boot:run`). Use ZGC — G1 gera pausas de centenas de ms que aparecem no p99:

```powershell
.\mvnw.cmd -DskipTests package
java -XX:+UseZGC -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

3. Consulte o produto seed (Caderno):

```powershell
curl http://localhost:8080/api/produtos/11111111-1111-1111-1111-111111111111
```

Health: `http://localhost:8080/actuator/health`

### Seeds

| id | nome | preco |
| --- | --- | --- |
| `11111111-1111-1111-1111-111111111111` | Caderno | 12.90 |
| `22222222-2222-2222-2222-222222222222` | Caneta | 3.50 |
| `33333333-3333-3333-3333-333333333333` | Lapis | 1.20 |

### Medir o caminho do banco (sem cache)

O GET usa Caffeine: o mesmo `id` não volta no Postgres depois do warmup. Para medir só o R2DBC:

```powershell
java -XX:+UseZGC -Dapp.cache.produtos.enabled=false -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

## Teste de carga (k6)

Instale o k6 (`winget install k6`). Com API e Postgres no ar, em **outro terminal**:

```powershell
k6 run load-test/get-produto.js
```

O script (`load-test/get-produto.js`) usa um único cenário `ramping-arrival-rate` (um pool de VUs, não a soma de três):

| Fase | Duração | Arrival rate |
| --- | --- | --- |
| Warmup | 10 s | 100 → 2.000 RPS |
| Ramp | 15 s | 2.000 → 10.000 RPS |
| Platô | 30 s | 10.000 RPS |

- `preAllocatedVUs: 500`, `maxVUs: 1000`, `discardResponseBodies: true`
- Thresholds: `http_req_failed` rate &lt; 1%, p99 &lt; 50 ms, `dropped_iterations` count == 0

Com mediana &lt; 1 ms bastam dezenas de VUs. Três cenários em paralelo (ou 3000 VUs no mesmo notebook) brigam com a API por CPU e socket.

### O que “passar” significa

- Throughput real ≈ 10.000 RPS no platô de 30 s
- `http_req_failed` &lt; 1% (0% no melhor run)
- p99 &lt; 50 ms — o sinal de sucesso, junto com 0% de erro
- `dropped_iterations == 0` é **frágil** num laptop Windows: o k6 descarta iterações se não houver VU livre no instante do arrival. Alguns drops não invalidam o throughput; veja a evolução abaixo.

Uma instância dá conta de ~10k TPS neste GET com cache. Cache + warmup/ramp + não superalocar VUs do k6 + `java -XX:+UseZGC -jar` pesaram mais do que um pool de 100 conexões.

## Resultados reais (evolução)

Números de runs neste ambiente (notebook Windows). O perfil atual é o da última linha.

| # | Configuração | Reqs | Overall | Platô (~10k) | dropped | avg | med | p99 | falhas | VUs |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Baseline: sem cache, pool 100, cliff 100→10k | 162.577 | 4.041/s | ~5,4k RPS | 138.427 | 314,87 ms | 336 ms | 513,78 ms | 0,05% | max 2000 |
| 2 | Caffeine, ainda cliff no warmup | 225.808 | 5.640/s | ~7,5k RPS | 75.189 | 32,86 ms | 1,99 ms | 506,2 ms | 0,21% | — |
| 3 | Warmup+ramp em 3 cenários (~3000 VUs por acidente) | 376.621 | 6.784/s | 276.700 req (~9,2k RPS) | 23.877 (ten_k) | — | 676 µs (ten_k) | 314,44 ms (ten_k) | 0,21% (todas no ten_k) | vus_max 3067 |
| 4 | **Melhor:** cenário único, pool de 500 VUs, jar + ZGC | **397.056** em 55 s | **7.219/s** | **≈ 9,9k RPS** | **3.379** | **2,45 ms** | **~0** | **35,13 ms (pass)** | **0%** (100% HTTP 200) | max 223 / vus_max 525 |

Detalhes do run 4 (melhor):

- p90 622 µs, p95 1,04 ms; outlier máximo 1,5 s
- 3.379 drops ≈ 0,84% das ~400k iterações planejadas — cerca de 1 s de stall a 10k RPS
- Threshold `dropped_iterations count==0` ainda falha; p99 35 ms + 0% de erro é o sinal que importa

**O que mudou o resultado:** cache Caffeine (tira o Postgres do caminho quente), ramp em vez de cliff, um único pool de VUs (não ~3000), e ZGC no jar. Aumentar o pool R2DBC para 100 não ajudou — aumentou contenção no Postgres.

## Como ler gargalos

`dropped_iterations` alto + latência de centenas de ms = a API não acompanha o arrival rate. O k6 esgota os VUs (`vus = maxVUs`) e descarta iterações. Pela lei de Little: `RPS ≈ VUs / latência`. Com 2000 VUs e ~315 ms, o teto é ~6k RPS — aumentar `maxVUs` só piora o p99.

Na stack reativa o teto deixa de ser threads Tomcat. O limite passa a ser:

| Gargalo | O que olhar |
| --- | --- |
| **Cache** | Hit em memória. Sem cache (`app.cache.produtos.enabled=false`), o teste vira Postgres-no-Docker, não a API. |
| **Pool R2DBC** | `spring.r2dbc.pool.max-size=32`. Pool grande demais (100) aumenta contenção no Postgres. |
| **Postgres / Docker no Windows** | Cada query atravessa o NAT do Docker Desktop. `fsync=off` só para bench local; `shm_size=256mb`; `max_connections=200`. |
| **GC vs mediana** | Mediana ~0 com p99 de centenas de ms costuma ser pausa de GC (G1), não o handler. ZGC reduz isso. |
| **VUs do k6** | Com mediana &lt; 1 ms, 200–500 VUs bastam. Superallocar (2000–3000) compete com a API por CPU e socket. |

JDBC/Hikari fica pequeno (2 conexões) e só serve o Flyway na subida.

## Testes funcionais

```powershell
.\mvnw.cmd test
```

Usa Testcontainers (PostgreSQL 16). O Docker precisa estar rodando.
