# Architecture Decision Record — payment-request-service

Este documento registra as decisões arquiteturais tomadas ao longo da implementação, seus trade-offs, limitações assumidas e possíveis evoluções futuras. As decisões são adicionadas à medida que são tomadas, não apenas ao final.

---

## ADR-001 — Arquitetura hexagonal (ports & adapters)

**Contexto:** o desafio exige separação clara entre domínio, aplicação, portas e adapters, permitindo evolução do domínio sem reescrita ampla.

**Decisão:** pacotes `domain` (modelo e regras de negócio, sem dependência de framework), `application` (casos de uso e portas de entrada/saída), `adapter` (implementações concretas: web, persistência, mensageria, integração externa) e `config` (infraestrutura transversal do Spring).

**Trade-offs:** mais boilerplate (interfaces de porta + implementação) do que uma abordagem "transaction script", mas isola o domínio de detalhes técnicos e facilita testes unitários sem subir contexto Spring.

---

## ADR-002 — Máquina de estados dentro do agregado `PaymentRequest`

**Decisão:** as transições de status (`CREATED→PROCESSING→{COMPLETED|REJECTED|FAILED}`) são métodos do próprio agregado, que validam a transição atual e lançam `InvalidStateTransitionException` para movimentos inválidos, além de registrar a entrada correspondente em `history`.

**Trade-offs:** mantém a regra de negócio centralizada no domínio (fácil de testar isoladamente), ao custo de o agregado carregar toda a lógica de validação de fluxo.

---

## ADR-003 — Retry técnico não gera transição `FAILED→PROCESSING` persistida

**Contexto:** a modelagem de estados prevê `FAILED → PROCESSING` como reprocessamento via Resilience4j (até 2 tentativas). Se cada tentativa técnica gerasse uma linha em `event_history`, duas falhas consecutivas para a mesma solicitação tentariam inserir duas linhas com o mesmo par `(payment_request_id, status=FAILED)`, violando a constraint única usada para idempotência (ver ADR-005).

**Decisão:** o `@Retry` do Resilience4j decora a chamada ao gateway externo *dentro* de uma única invocação do caso de uso de processamento. O laço `FAILED→PROCESSING` do diagrama de estados representa esse retry técnico transparente na camada de integração. Apenas transições que mudam o estado observável do agregado são persistidas: `CREATED→PROCESSING` (uma vez) e `PROCESSING→{COMPLETED|REJECTED|FAILED}` (uma vez, após todas as tentativas se esgotarem ou a primeira resposta definitiva do gateway).

**Trade-off assumido:** o histórico persistido não mostra cada tentativa técnica individual — essa informação fica disponível apenas nos logs estruturados (correlacionados por `correlationId` e `paymentRequestId`), não no histórico de domínio. Evolução futura: se for necessário auditar tentativas individuais, criar uma tabela separada `processing_attempt` (fora do escopo de `event_history`, que representa apenas transições de estado do agregado).

---

## ADR-004 — Sem ferramenta dedicada de migrations (Flyway/Liquibase)

**Decisão:** o schema é criado via Hibernate (`spring.jpa.hibernate.ddl-auto=update`), sem Flyway/Liquibase.

**Justificativa:** redução de complexidade para o escopo do exercício — não há necessidade de versionamento de schema multi-ambiente para um exercício local de curta duração.

**Limitação assumida:** essa abordagem não é recomendada para produção (falta de controle explícito e versionado do schema, risco de `ddl-auto` aplicar mudanças não revisadas). Evolução futura: adotar Flyway com migrations versionadas antes de qualquer uso além do escopo do desafio.

---

## ADR-005 — Idempotência via constraint `UNIQUE (payment_request_id, status)`

**Decisão:** a tabela `event_history` possui a constraint `UNIQUE (payment_request_id, new_status)`. O consumidor do evento tenta inserir o registro de histórico **antes** de aplicar qualquer efeito colateral (chamar o gateway externo, publicar novo evento). Se a inserção violar a constraint (`DataIntegrityViolationException`), o evento é tratado como duplicata (reentrega do Kafka, por exemplo) e descartado sem reprocessamento.

**Por que não uma checagem manual "verificar antes de agir":** um fluxo do tipo `SELECT ... verifica se já existe ... depois INSERT` está sujeito a condição de corrida entre múltiplas instâncias do consumidor (ou reentregas concorrentes) — duas checagens podem passar simultaneamente antes de qualquer uma inserir. A constraint `UNIQUE` desloca a garantia de atomicidade para o banco de dados: apenas uma inserção concorrente pode ter sucesso, e a segunda falha de forma determinística, sem janela de corrida.

---

## ADR-006 — Sem métricas neste exercício

**Decisão:** métricas (Prometheus/Micrometer) não são implementadas nesta entrega.

**Justificativa:** ausência de um ambiente local de observabilidade (tipo DataDog/Grafana) para consumir e visualizar essas métricas, o que geraria complexidade desnecessária ao escopo do desafio sem benefício demonstrável localmente.

**Evolução futura:** caso fosse necessário, a biblioteca escolhida seria o **Micrometer** (nativamente integrado ao Spring Boot Actuator), expondo métricas como contadores de solicitações por status, latência de chamada ao gateway externo e taxa de retry/falha.

---

## ADR-007 — Mock de integração externa embutido no próprio serviço

**Contexto:** o desafio pede que a integração externa (validação/efetivação de pagamento) seja simulada localmente e consumida via `FeignClient`, sem chamar serviço real de nuvem.

**Decisão:** o mock é um `@RestController` interno ao próprio `app` (`MockPaymentGatewayController`), exposto em `/mock/payment-gateway/**`, chamado pelo `PaymentGatewayFeignClient` apontando para `localhost`. O comportamento (sucesso/rejeição/falha técnica) é determinístico e controlável via input da requisição, permitindo demonstrar os três desfechos possíveis do fluxo.

**Alternativa descartada:** subir um serviço de mock (ex.: WireMock) como container adicional no `docker-compose`. Descartada por adicionar complexidade de infraestrutura sem justificar o ganho, já que o contrato de request/response fica igualmente explícito com um controller local — a decisão preserva a separação entre domínio/porta (`PaymentGatewayPort`) e adapter (`FeignClient` + mock), que é o que o desafio realmente avalia.

---

## ADR-008 — Sem endpoint público de atualização manual de status

**Decisão:** não existe um endpoint `PATCH /payment-requests/{id}/status` de uso livre. A transição de status é resultado do fluxo assíncrono: criação publica `PaymentRequestCreated`, o próprio serviço consome esse evento e conduz a solicitação por `PROCESSING` até um estado terminal.

**Justificativa:** expor uma transição de estado arbitrária via API contornaria a máquina de estados do agregado e não reflete um caso de uso real do domínio. O requisito funcional "atualizar status conforme o fluxo modelado" é atendido pelo fluxo interno orientado a eventos, que é o mecanismo de atualização de fato.

---

## ADR-009 — Persistência: entidades JPA próprias, mapeadas manualmente a partir do agregado de domínio

**Contexto:** a Etapa 2 (persistência) precisa mapear o agregado `PaymentRequest` (que não pode depender de JPA, por ADR-001) para as tabelas `payment_request` e `event_history`.

**Decisão:** `PaymentRequestJpaEntity` e `EventHistoryJpaEntity` (pacote `adapter.persistence.entity`) são classes de mapeamento dedicadas, independentes das classes de domínio. Um mapper estático (`PaymentRequestEntityMapper`) converte em ambas as direções. O agregado é reidratado via `PaymentRequest.reconstitute(...)` (já previsto desde a Etapa 1). A tabela `event_history` usa a constraint `UNIQUE (payment_request_id, new_status)` definida em ADR-005.

**Estratégia de save idempotente:** como o agregado sempre carrega o histórico completo em memória (não apenas os eventos novos), o adapter (`PaymentRequestRepositoryAdapter`) busca a entidade existente por id; se encontrada, `updateEntity` insere apenas as linhas de `history` cujo `newStatus` ainda não está persistido (evita tentar reinserir uma linha já existente, o que violaria a constraint única sem necessidade). Se não encontrada, persiste a entidade nova com todo o histórico.

**Trade-off assumido:** duas chamadas ao banco por `save` (find + save) em vez de uma única `upsert`; aceitável no escopo do exercício, dado o volume esperado e a simplicidade resultante.

---

## ADR-010 — Ajustes de dependências de teste para compatibilidade com Spring Boot 4.0.7 e Docker Desktop atual

**Contexto:** ao implementar o primeiro teste de integração com Testcontainers (Etapa 2), duas incompatibilidades de ambiente/versão surgiram e precisaram ser corrigidas:

1. **Fatias de teste do Spring Boot 4 foram modularizadas.** `@DataJpaTest` e `@AutoConfigureTestDatabase` deixaram de fazer parte de `spring-boot-starter-test` e passaram a viver em módulos próprios (`org.springframework.boot.data.jpa.test.autoconfigure` e `org.springframework.boot.jdbc.test.autoconfigure`, respectivamente). Foi necessário adicionar a dependência `org.springframework.boot:spring-boot-starter-data-jpa-test` (`testImplementation`) para disponibilizar essas anotações.
2. **Testcontainers 1.21.3 é incompatível com Docker Engine 29** (bundle do Docker Desktop instalado localmente): o cliente HTTP interno (docker-java) negocia por padrão uma versão de API antiga (1.32) que o daemon rejeita com `400 Bad Request`, impedindo `NpipeSocketClientProviderStrategy` de detectar o Docker no Windows. A correção adotada foi atualizar o `testcontainers-bom` para `2.0.5` (linha que resolve essa negociação de versão), o que exigiu renomear os artefatos de módulo usados (`org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`, mesma convenção para `postgresql` e `kafka`), sem alteração de código nos testes além dos imports do `@DataJpaTest`.

**Decisão:** manter as versões corrigidas (`spring-boot-starter-data-jpa-test` + `testcontainers-bom:2.0.5` com artefatos prefixados) documentadas aqui para que o ambiente seja reproduzível sem re-descobrir esses dois problemas.

**Trade-off assumido:** nenhum — são correções de compatibilidade, não decisões de design; registradas por transparência, já que a Regra 3 do desafio pede que decisões tomadas ao longo do processo (mesmo as técnicas/operacionais) fiquem documentadas.