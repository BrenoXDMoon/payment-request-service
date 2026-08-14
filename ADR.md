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

---

## ADR-011 — Escopo da Etapa 3 (eventos): consumo de `PaymentRequestCreated` limitado à transição `CREATED→PROCESSING`

**Contexto:** a Etapa 3 cobre a infraestrutura de mensageria Kafka (publicação e consumo de eventos de domínio). A transição `PROCESSING→{COMPLETED|REJECTED|FAILED}` depende da chamada ao gateway externo de pagamento (`PaymentGatewayPort` + `FeignClient` + mock), que é objeto da Etapa 4 (API REST).

**Decisão:** nesta etapa, o consumidor Kafka (`PaymentRequestCreatedEventListener`) reage ao evento `PaymentRequestCreated` publicado na criação e conduz apenas `CREATED→PROCESSING` (via `ProcessPaymentRequestUseCase`/`ProcessPaymentRequestService`), persistindo a transição e publicando `PaymentRequestStatusChanged` no tópico correspondente. A finalização do fluxo (chamada ao gateway com retry via Resilience4j e transição para o estado terminal) fica para a Etapa 4, quando `PaymentGatewayPort` ganha implementação concreta.

**Justificativa:** evita introduzir uma porta de saída (`PaymentGatewayPort`) sem adapter real nesta etapa, o que exigiria um bean "provisório" apenas para o contexto Spring subir — múltiplas implementações provisórias geram retrabalho e um histórico de commits menos claro sobre o que de fato foi entregue em cada etapa. A separação também espelha a extensão natural do desafio: "eventos" (mensageria) e "API REST" (endpoints HTTP + integração externa) são etapas distintas no plano aprovado.

**Trade-off assumido:** entre a Etapa 3 e a Etapa 4, uma solicitação de pagamento pode ficar "parada" em `PROCESSING` sem nunca alcançar um estado terminal — aceitável, pois é um estado intermediário do desenvolvimento incremental, não do sistema em produção.

---

## ADR-012 — Publicação de eventos após persistir (sem outbox transacional)

**Decisão:** `CreatePaymentRequestService` e `ProcessPaymentRequestService` persistem a transição do agregado (`repository.save(...)`) e, em seguida, publicam os eventos de domínio pendentes (`pullDomainEvents()` + `DomainEventPublisher.publish`) como duas operações distintas, sem transação distribuída nem padrão *transactional outbox*.

**Trade-off assumido:** existe uma janela onde o `save` no PostgreSQL é bem-sucedido, mas a publicação no Kafka falha (processo interrompido, broker indisponível) — o evento seria perdido e a solicitação ficaria presa no estado persistido sem que o restante do fluxo fosse notificado. Aceito para o escopo do exercício, dado o ambiente local de curta duração; evolução futura: implementar *outbox pattern* (tabela `outbox_event` gravada na mesma transação do agregado + processo separado publicando para o Kafka) caso o serviço evolua para produção.

---

## ADR-013 — Ajustes de dependências para compatibilidade com Spring Boot 4.0.7 (Kafka, Resilience4j, Spring Cloud)

**Contexto:** ao implementar a Etapa 3 (eventos) e subir o primeiro teste de contexto completo (`@SpringBootTest`) com Kafka via Testcontainers, três incompatibilidades adicionais de versão surgiram (Spring Boot 4.0.7 é uma versão muito recente, e parte do ecossistema ainda está migrando):

1. **Autoconfiguração do Kafka foi extraída de `spring-boot-autoconfigure` para um módulo próprio.** Assim como `@DataJpaTest` (ADR-010), `KafkaAutoConfiguration`/`KafkaProperties` deixaram de existir em `spring-boot-autoconfigure` e passaram a viver em `org.springframework.boot:spring-boot-kafka` (pacote `org.springframework.boot.kafka.autoconfigure`). Foi necessário trocar a dependência `org.springframework.kafka:spring-kafka` por `org.springframework.boot:spring-boot-starter-kafka` (que traz o novo módulo de autoconfiguração + `spring-kafka` transitivamente); sem isso, nenhum bean `KafkaTemplate`/`KafkaListenerContainerFactory` era criado e o contexto Spring falhava ao subir o listener.
2. **`resilience4j-spring-boot3` não é compatível com Spring Boot 4** — a partir da versão 2.3.0 ele referencia classes (`RxJava3FallbackDecorator` em `resilience4j-spring6`) que exigiam alinhar a versão via `resilience4j-bom`, e mesmo assim o módulo possui um verificador de compatibilidade em runtime (`SpringBoot3Verifier`) que lança `IncompatibleSpringBootVersionException` para qualquer Spring Boot ≥ 4. A correção foi trocar para o artefato `io.github.resilience4j:resilience4j-spring-boot4:2.4.0` (módulo dedicado ao Spring Boot 4, lançado junto da versão 2.4.0 do resilience4j, ainda não coberto pelo `resilience4j-bom:2.4.0` — por isso fixado com versão explícita).
3. **`spring-cloud-starter-openfeign` (via `spring-cloud-dependencies:2025.0.0`) referenciava `org.springframework.boot.web.context.WebServerInitializedEvent`**, classe reorganizada de pacote no Spring Boot 4. A correção foi atualizar o BOM do Spring Cloud para `2025.1.2` (trem de release alinhado ao Spring Boot 4).

**Decisão:** manter essas versões documentadas aqui pelo mesmo motivo do ADR-010 — reprodutibilidade do ambiente sem precisar re-descobrir os três problemas. `resilience4j-bom` foi adicionado a `dependencyManagement.imports` para manter todos os módulos `io.github.resilience4j:*` (exceto `resilience4j-spring-boot4`, ainda não coberto pelo BOM) em versões mutuamente compatíveis.

**Trade-off assumido:** nenhum — correções de compatibilidade, não decisões de design.

---

## ADR-014 — Etapa 4: camada REST e integração com gateway via Feign

**Contexto:** a Etapa 4 fecha o fluxo completo do agregado, cobrindo os dois pontos que faltavam desde a Etapa 3 (ver ADR-011): expor `PaymentRequest` via API HTTP e implementar de fato a chamada ao gateway externo de pagamento que conduz `PROCESSING→{COMPLETED|REJECTED|FAILED}`.

**Decisão — API REST:** `PaymentRequestController` expõe apenas `POST /payment-requests` (cria e retorna 201 com `Location`) e `GET /payment-requests/{id}` (retorna 404 via `PaymentRequestNotFoundException` quando não encontrado), consumindo as portas de entrada `CreatePaymentRequestUseCase`/`GetPaymentRequestUseCase` — sem endpoint de atualização manual de status, conforme já decidido na ADR-008. Erros são padronizados por um único `@RestControllerAdvice` (`GlobalExceptionHandler`), que traduz `PaymentRequestNotFoundException`→404, `IllegalArgumentException`/`InvalidStateTransitionException`→400, `MethodArgumentNotValidException`→400 (mensagem agregada por campo) e qualquer outra exceção→500, sempre no mesmo formato de corpo (`timestamp`/`status`/`error`/`message`).

**Decisão — integração com o gateway:** `PaymentGatewayPort` (porta de saída da aplicação) é implementada por `PaymentGatewayAdapter`, que delega a um `PaymentGatewayFeignClient` (`@FeignClient` apontando para `app.payment-gateway.base-url`, hoje o `MockPaymentGatewayController` embutido no próprio serviço — ver ADR-007) e usa DTOs próprios do adapter (`PaymentGatewayRequestDto`/`PaymentGatewayResponseDto`), sem vazar tipos do Feign para a camada de aplicação. O método `process` é anotado com `@Retry(name = "paymentGateway")` (Resilience4j, configurado em `application.yaml` com 2 tentativas), e qualquer `FeignException` (timeout, 5xx, conexão recusada) é convertida em `PaymentGatewayUnavailableException` do domínio — que é justamente a exceção configurada como `retry-exceptions` do Resilience4j. Se o retry se esgotar, `ProcessPaymentRequestService` captura a exceção e conduz o agregado a `FAILED` (sem persistir o laço técnico de retry, conforme ADR-003).

**Trade-off assumido:** a URL do gateway mock aponta para `localhost` fixo (mesma porta HTTP do próprio serviço), o que é adequado para o exercício (ADR-007) mas exige atenção ao portar para um ambiente real com gateway externo de fato — nesse caso `app.payment-gateway.base-url` passaria a apontar para outro host/serviço, sem qualquer mudança de código.

---

## ADR-015 — Etapa 6: correlação de requisições via `X-Correlation-Id` propagado por header Kafka

**Contexto:** o requisito não funcional de observabilidade pede "logs estruturados e correlação de requisições que permitam investigar incidentes". O padrão de log em `application.yaml` já reservava `%X{correlationId}` desde a Etapa 0, mas nenhum componente populava esse valor no MDC — cada linha de log era emitida com `correlationId=` vazio. Além disso, o fluxo real de uma solicitação atravessa uma fronteira assíncrona (requisição HTTP → evento Kafka → consumidor → chamada ao gateway → novo evento Kafka), então correlacionar apenas a thread da requisição HTTP não seria suficiente para investigar um incidente ponta a ponta.

**Decisão:** `CorrelationIdFilter` (`adapter.web.filter`, um `OncePerRequestFilter`) intercepta toda requisição HTTP: reaproveita o header `X-Correlation-Id` se o cliente enviar um, ou gera um novo UUID; coloca o valor no MDC (`correlationId`) durante o processamento da requisição, devolve o mesmo valor no header de resposta, e limpa o MDC no `finally`. Para propagar esse valor através do Kafka, `KafkaDomainEventPublisher.publish` lê o `correlationId` corrente do MDC e o anexa como header do `ProducerRecord`; `PaymentRequestCreatedEventListener` lê esse header (`byte[]`, decodificado manualmente para evitar depender de conversão implícita do Spring Messaging) e repõe o valor no MDC da thread do consumidor Kafka antes de processar o evento, limpando no `finally`. Como o restante do fluxo (`ProcessPaymentRequestService`, `PaymentGatewayAdapter`, repositório, e a publicação do evento `PaymentRequestStatusChanged` seguinte) roda de forma síncrona na mesma thread do listener, todos os logs dessa cadeia carregam automaticamente o mesmo `correlationId` — inclusive o novo evento publicado, preparando o terreno para um futuro consumidor de `PaymentRequestStatusChanged` continuar a mesma cadeia de correlação.

**Alternativa descartada:** usar apenas um `ThreadLocal`/MDC sem propagação via header Kafka. Descartada porque o MDC é local à thread — sem propagar o valor através da mensagem, o consumidor Kafka (rodando em thread separada do listener container) geraria um `correlationId` novo e desconectado do que originou a requisição HTTP, quebrando a correlação exatamente na fronteira assíncrona que mais precisa de rastreabilidade.

**Trade-off assumido:** nenhum consumidor atual lê o tópico `payment.request.status-changed`, então a propagação do header nesse segundo evento não tem efeito observável hoje — é uma decisão feita pensando na evolução do sistema (ADR já cobre isso como requisito não funcional de "Evolução"), sem custo adicional relevante.