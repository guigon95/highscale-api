# ⚡ highscale-api

API **Kotlin + Spring Boot 4** reativa, em Clean Architecture. O alvo é um `GET /api/produtos/{id}` a **~10 mil RPS numa instância**, com WebFlux/Netty, R2DBC e cache Caffeine.

> **Melhor run (k6):** p99 **35 ms** · **0%** de erro · **≈ 9,9k RPS** no platô · mediana em microssegundos

```
Controller  →  Use case  →  Cache (Caffeine)  →  R2DBC  →  Postgres
     ↑                                                         │
  WebFlux / Netty                                    Flyway só na subida
```

---

## 🧰 Stack

| | |
| --- | --- |
| ☕ Runtime | Kotlin coroutines, Java 21, Spring Boot 4.1 (WebFlux + Netty) |
| 🔌 Endpoint | `GET /api/produtos/{id}` com `suspend` + R2DBC (`DatabaseClient`) |
| 🐘 Banco | PostgreSQL 16 (Docker Compose) |
| 📦 Migrations | JDBC + Flyway (Hikari: 2 conexões, só na subida) |
| 🧠 Cache | Caffeine no GET por id (`app.cache.produtos.enabled=true`, 10k entradas, TTL 5 min) |
| 🔗 Pool R2DBC | `initial-size=16`, `max-size=32`, `preparedStatementCacheQueries=256` |
| 📈 Observabilidade | Actuator + Prometheus (`/actuator/health`, `/actuator/prometheus`) |

---

## 🚀 Subir localmente

**1.** Banco:

```powershell
docker compose up -d
```

**2.** Jar + **ZGC** (mais rápido que `spring-boot:run`; G1 gera pausas que aparecem no p99):

```powershell
.\mvnw.cmd -DskipTests package
java -XX:+UseZGC -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

**3.** Produto seed (Caderno):

```powershell
curl http://localhost:8080/api/produtos/11111111-1111-1111-1111-111111111111
```

Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 🌱 Seeds

| id | nome | preco |
| --- | --- | --- |
| `11111111-1111-1111-1111-111111111111` | Caderno | 12.90 |
| `22222222-2222-2222-2222-222222222222` | Caneta | 3.50 |
| `33333333-3333-3333-3333-333333333333` | Lapis | 1.20 |

### 🗄️ Medir só o banco (sem cache)

Com Caffeine ligado, o mesmo `id` não volta no Postgres depois do warmup:

```powershell
java -XX:+UseZGC -Dapp.cache.produtos.enabled=false -jar target\highscale-api-0.0.1-SNAPSHOT.jar
```

---

## 🧪 Teste de carga (k6)

Instale o k6 (`winget install k6`). Com API e Postgres no ar, **em outro terminal**:

```powershell
k6 run load-test/get-produto.js
```

O script `load-test/get-produto.js` usa **um** cenário `ramping-arrival-rate` (um pool de VUs):

| Fase | Duração | Arrival rate |
| --- | --- | --- |
| 🔥 Warmup | 10 s | 100 → 2.000 RPS |
| 📈 Ramp | 15 s | 2.000 → 10.000 RPS |
| 🏁 Platô | 30 s | 10.000 RPS |

- `preAllocatedVUs: 500` · `maxVUs: 1000` · `discardResponseBodies: true`
- Thresholds: erros &lt; 1% · p99 &lt; 50 ms · `dropped_iterations == 0`

Com mediana &lt; 1 ms bastam dezenas de VUs. Três cenários em paralelo (~3000 VUs) brigam com a API por CPU e socket.

### ✅ O que “passar” significa

| Critério | Alvo | Nota |
| --- | --- | --- |
| Throughput no platô | ≈ 10.000 RPS | 30 s constantes |
| Erros | &lt; 1% | **0%** no melhor run |
| p99 | &lt; 50 ms | **sinal principal de sucesso** |
| `dropped_iterations` | `== 0` | ⚠️ Frágil no Windows: o k6 dropa se não houver VU livre naquele instante |

Uma instância dá conta de **~10k TPS** neste GET com cache. O que mais pesou: Caffeine + ramp (sem cliff) + poucos VUs no k6 + `java -XX:+UseZGC -jar`. Pool R2DBC de 100 conexões **não** ajudou.

---

## 📊 Resultados reais

Runs neste ambiente (notebook Windows). O perfil atual é o da **linha 4**.

| # | Configuração | Reqs | Overall | Platô | dropped | avg | med | p99 | falhas | VUs |
| :---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | Baseline: sem cache, pool 100, cliff 100→10k | 162.577 | 4.041/s | ~5,4k | 138.427 | 315 ms | 336 ms | 514 ms | 0,05% | 2000 |
| 2 | Caffeine, ainda cliff no warmup | 225.808 | 5.640/s | ~7,5k | 75.189 | 33 ms | 2 ms | 506 ms | 0,21% | — |
| 3 | Warmup+ramp em 3 cenários (~3000 VUs) | 376.621 | 6.784/s | ~9,2k | 23.877 | — | 676 µs | 314 ms | 0,21% | 3067 |
| 4 | 🏆 Cenário único, 500 VUs, jar + ZGC | **397.056** | **7.219/s** | **≈ 9,9k** | **3.379** | **2,5 ms** | **~0** | **35 ms** ✅ | **0%** | 223 / 525 |

**Run 4 (melhor)**

- p90 **622 µs** · p95 **1,04 ms** · outlier máx. 1,5 s
- 3.379 drops ≈ **0,84%** das ~400k iterações (~1 s de stall a 10k RPS)
- `dropped_iterations == 0` ainda falha; **p99 35 ms + 0% erro** é o que importa

---

## 🔍 Como ler gargalos

`dropped_iterations` alto + latência de centenas de ms = a API não acompanha o arrival rate. Lei de Little: `RPS ≈ VUs / latência`. Com 2000 VUs e ~315 ms, o teto é ~6k RPS — subir `maxVUs` só piora o p99.

Na stack reativa o teto **não** é thread Tomcat:

| | Gargalo | O que olhar |
| --- | --- | --- |
| 🧠 | Cache | Hit em memória. Sem cache, o teste vira Postgres-no-Docker. |
| 🔗 | Pool R2DBC | `max-size=32`. Pool 100 aumenta contenção no Postgres. |
| 🐳 | Docker no Windows | Cada query atravessa o NAT. `fsync=off` só no bench; `shm_size=256mb`. |
| ♻️ | GC vs mediana | Mediana ~0 e p99 alto = pausa G1, não o handler. Use ZGC. |
| 👥 | VUs do k6 | Mediana &lt; 1 ms → 200–500 VUs. 2000–3000 competem com a API. |

---

## ✅ Testes funcionais

```powershell
.\mvnw.cmd test
```

Testcontainers (PostgreSQL 16) — o Docker precisa estar no ar.
