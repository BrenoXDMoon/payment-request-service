# Declaração de uso de IA

Este projeto foi desenvolvido com apoio de IA generativa. Este arquivo é atualizado incrementalmente, refletindo com precisão o que foi de fato produzido, revisado ou apenas apoiado por IA em cada etapa — sem omissões ou generalizações.

**Ferramenta de IA utilizada:** Claude Code (Anthropic), modelo Claude Sonnet 5.

---

## Etapa 0 — Plano de implementação e setup inicial

**Prompt principal enviado:**
> "leia o arquivo docs/ai-initial-context.md e gere o passo à passo para implementação no diretório /app"

**O que foi gerado por IA:**
- Leitura de `docs/ai-initial-context.md` e `docs/desafio_tecnico_jornada_pagamentos_v2.pdf` (documento original do desafio) para consolidar os requisitos.
- Plano de implementação completo (estrutura de pacotes hexagonal, modelagem do agregado `PaymentRequest`, eventos de domínio, endpoints REST, estratégia de persistência PostgreSQL, tópicos Kafka e formato de mensagem, estratégia de testes, pontos de observabilidade e decisão sobre mocks), incluindo decisões de design para resolver ambiguidades da especificação (ex.: como o laço `FAILED→PROCESSING` do retry se relaciona com a constraint única de idempotência — ver `ADR.md`, ADR-003).
- Plano revisado e aprovado explicitamente pelo autor antes de qualquer código ser escrito, conforme exigido pela Regra 1 do documento de contexto.
- Setup inicial gerado 100% por IA: `app/build.gradle` (dependências), `app/src/main/resources/application.yaml` (configuração), `docker-compose.yml` (PostgreSQL + Kafka + Kafka UI), esqueleto deste arquivo e do `ADR.md`.

**Revisão humana:** as versões de bibliotecas (Spring Cloud, Instancio, springdoc, Resilience4j) foram consultadas pela IA diretamente no Maven Central antes de serem fixadas no `build.gradle`; a compatibilidade efetiva com Spring Boot 4.0.7 ainda precisa ser validada rodando o build.

---

## Etapa 1 — Domínio

**Prompt principal enviado:**
> "pode seguir"

**O que foi gerado por IA:**
- Pacotes `domain.model`, `domain.event`, `domain.exception`.
- `PaymentStatus` (máquina de estados via `allowedNextStates()` por constante do enum), `Money` (Value Object com validação de valor positivo e código ISO 4217), `EventHistory` (registro imutável de transição), agregado `PaymentRequest` (factory `create`, factory de reidratação `reconstitute`, métodos de transição `startProcessing/complete/reject/fail` que validam a máquina de estados e acumulam eventos de domínio via `pullDomainEvents()`).
- Eventos de domínio `PaymentRequestCreatedEvent` e `PaymentRequestStatusChangedEvent`, e exceções `InvalidStateTransitionException`, `PaymentRejectedException`, `PaymentGatewayUnavailableException`.
- Testes unitários (`MoneyTest`, `PaymentStatusTest`, `PaymentRequestTest`) com JUnit 5 + Instancio (geração de `origin`/`destination`/`context`), cobrindo transições válidas/inválidas, imutabilidade do histórico exposto e limpeza de eventos pendentes.
- Remoção do teste `AppApplicationTests` (placeholder do Spring Initializr, sem asserts) — será substituído por um teste de contexto real apoiado em Testcontainers quando a camada de persistência/mensageria existir.

**Revisão humana:** build (`./gradlew test`) executado e validado pela IA após a implementação — 32 testes, 0 falhas.

---

## Etapa 2 — Persistência (PostgreSQL / JPA)

**Prompt principal enviado:**
> "continue a implementação do ponto que paramos"

**O que foi gerado por IA:**
- Porta de saída `application.port.out.PaymentRequestRepository` (interface `save`/`findById`), consumida futuramente pelos casos de uso da camada de aplicação.
- Entidades JPA em `adapter.persistence.entity`: `PaymentRequestJpaEntity` (tabela `payment_request`) e `EventHistoryJpaEntity` (tabela `event_history`, com a constraint única `UNIQUE (payment_request_id, new_status)` definida em ADR-005).
- `PaymentRequestJpaRepository` (Spring Data JPA) e `PaymentRequestRepositoryAdapter`, que implementa a porta de saída usando o repositório Spring Data + o mapper.
- `PaymentRequestEntityMapper` (mapeamento manual, estático, sem MapStruct): `toEntity`, `toDomain` (via `PaymentRequest.reconstitute`) e `updateEntity` — este último insere apenas as linhas de histórico ainda não persistidas, dando idempotência ao `save` sem depender de upsert no banco (ver ADR-009).
- Teste de integração `PaymentRequestRepositoryAdapterIT` (`@DataJpaTest` + Testcontainers `PostgreSQLContainer`), cobrindo: round-trip completo de criação/reidratação, persistência incremental de apenas o histórico novo em um segundo `save` após uma transição de estado, e retorno vazio para id inexistente.
- Duas correções de compatibilidade de ambiente descobertas ao rodar os testes pela primeira vez (detalhadas em ADR-010): adição de `spring-boot-starter-data-jpa-test` (as fatias de teste do Spring Boot 4 foram modularizadas — `@DataJpaTest` não está mais em `spring-boot-starter-test`) e upgrade do `testcontainers-bom` de `1.21.3` para `2.0.5` (a versão anterior não negocia corretamente a API do Docker Engine 29 do Docker Desktop instalado localmente, retornando 400 Bad Request e impedindo o Testcontainers de detectar o Docker no Windows).

**Revisão humana:** build (`./gradlew test`) executado pela IA com Docker Desktop ativo localmente — 35 testes, 0 falhas, incluindo o teste de integração real contra PostgreSQL via Testcontainers.

---

## Etapa 3 — Eventos (Kafka)

**Prompt principal enviado:**
> "podemos seguir para a etapa do kafka"

**O que foi gerado por IA:**
- Antes de codificar, a IA leu o histórico da sessão anterior (ADR.md, AI_USAGE.md, README.md, código já existente) para reconstruir o estado do projeto, já que a conversa era nova — não havia memória de sessão anterior disponível.
- Portas de aplicação: `DomainEventPublisher` (saída, genérica para qualquer `DomainEvent`), `CreatePaymentRequestUseCase`/`CreatePaymentRequestCommand` e `StartProcessingUseCase` (entrada).
- Serviços de aplicação `CreatePaymentRequestService` (cria o agregado, persiste e publica `PaymentRequestCreated`) e `StartProcessingService` (consome o evento de criação, conduz `CREATED→PROCESSING`, com tratamento de idempotência para eventos duplicados/fora de ordem via `InvalidStateTransitionException` e `DataIntegrityViolationException` — ver ADR-005).
- Adapter de mensageria (`adapter.messaging`): `KafkaDomainEventPublisher` (roteia cada tipo de evento de domínio para o tópico configurado, chave de partição = `paymentRequestId`) e `PaymentRequestCreatedEventListener` (`@KafkaListener` que aciona `StartProcessingUseCase`).
- Decisão de escopo registrada em ADR-011: a transição `PROCESSING→terminal` (que depende do gateway externo, ainda não implementado) fica para a Etapa 4, evitando um adapter provisório apenas para o contexto Spring subir.
- Trade-off de "save then publish" sem outbox transacional registrado em ADR-012.
- Teste de integração ponta a ponta `PaymentRequestEventFlowIT` (`@SpringBootTest` completo + Testcontainers PostgreSQL e Kafka): cria a solicitação via `CreatePaymentRequestUseCase` e usa Awaitility para aguardar o consumidor mover o status para `PROCESSING` de forma assíncrona via Kafka real. Testes unitários dos dois serviços com Mockito (`CreatePaymentRequestServiceTest`, `StartProcessingServiceTest`).
- Três incompatibilidades adicionais de ambiente/versão com Spring Boot 4.0.7 foram descobertas e corrigidas ao rodar o teste de contexto completo pela primeira vez (detalhadas em ADR-013): troca de `spring-kafka` por `org.springframework.boot:spring-boot-starter-kafka` (autoconfiguração do Kafka foi extraída para módulo próprio no Boot 4), troca de `resilience4j-spring-boot3` por `resilience4j-spring-boot4:2.4.0` (o módulo `spring-boot3` recusa-se a rodar em Spring Boot 4 via verificação em runtime) e upgrade do `spring-cloud-dependencies` de `2025.0.0` para `2025.1.2` (trem de release do Spring Cloud alinhado ao Spring Boot 4).

**Revisão humana:** build (`./gradlew test`) executado pela IA com Docker Desktop ativo localmente — 41 testes, 0 falhas, incluindo o fluxo de ponta a ponta real via Kafka (Testcontainers) além dos testes já existentes de domínio e persistência.

---

## Etapa 4 — API REST e integração com gateway de pagamento

**Nota sobre esta entrada:** a Etapa 4 foi implementada em uma sessão anterior do Claude Code, cuja conversa não estava disponível nesta sessão (nova conversa, sem memória do histórico de prompts). Por isso, o registro abaixo foi reconstruído por inspeção direta do código gerado, em vez de citar o prompt original — mantendo a precisão exigida por este arquivo sem inventar uma citação.

**O que foi gerado por IA (identificado por inspeção de código):**
- Camada web (`adapter.web`): `PaymentRequestController` (`POST /payment-requests` e `GET /payment-requests/{id}`), DTOs `CreatePaymentRequestRequest`/`PaymentRequestResponse`/`EventHistoryResponse`, e `GlobalExceptionHandler` (`@RestControllerAdvice`) padronizando o corpo de erro para not-found/validação/bad-request/erro inesperado.
- Integração com o gateway externo (`adapter.external.gateway`): `PaymentGatewayAdapter` implementando a porta `PaymentGatewayPort`, delegando a `PaymentGatewayFeignClient` (`@FeignClient`) com DTOs próprios do adapter, convertendo `FeignException` em `PaymentGatewayUnavailableException` do domínio e anotado com `@Retry(name = "paymentGateway")` (Resilience4j).
- `MockPaymentGatewayController` (`adapter.web.mock`), simulando os três desfechos do gateway (aprovado / rejeitado por regra de negócio / indisponível) de forma determinística a partir do payload da requisição — ver ADR-007.
- `ProcessPaymentRequestUseCase`/`ProcessPaymentRequestService`, que substituiu o `StartProcessingUseCase`/`StartProcessingService` da Etapa 3: agora conduz o fluxo completo `CREATED→PROCESSING→{COMPLETED|REJECTED|FAILED}`, chamando o gateway real após iniciar o processamento.
- `OpenApiConfig` e anotações springdoc nos endpoints, expondo a documentação em `/swagger-ui.html`.
- Testes: `PaymentGatewayAdapterTest` (unitário, Mockito, cobrindo mapeamento de resposta aprovada/rejeitada e tradução de `FeignException`) e `ProcessPaymentRequestServiceTest` (unitário, cobrindo aprovado/rejeitado/indisponível/não encontrado/evento duplicado/conflito de constraint única).
- Decisões formalizadas em ADR-014 (camada REST + integração via Feign), com correção de referências desatualizadas nas ADR-011/ADR-012 (nomes antigos `StartProcessingUseCase`/`StartProcessingService`).

**Revisão humana (nesta sessão):** build (`./gradlew clean test`) executado com Docker Desktop ativo localmente — 46 testes, 0 falhas. Identificado e corrigido um gap de cobertura: o teste de integração ponta a ponta (`PaymentRequestEventFlowIT`) rodava com `@SpringBootTest` em modo `MOCK` (sem servidor HTTP real), então a chamada Feign ao gateway nunca era de fato exercitada — o teste validava apenas `CREATED→PROCESSING`, não a finalização do fluxo. Corrigido trocando para `webEnvironment = DEFINED_PORT` e estendendo as asserções (ver ADR-014 e commit correspondente).

---

## Etapa 6 — Observabilidade (correlação de requisições)

**Prompt principal enviado:**
> "retoma pela pendência imediata, depois segue pra observabilidade"

**O que foi gerado por IA:**
- Diagnóstico: o padrão de log em `application.yaml` já referenciava `%X{correlationId}` desde a Etapa 0, mas nenhum componente populava esse valor — todo log saía com `correlationId=` vazio, e o requisito não funcional de "correlação de requisições" não estava de fato atendido.
- `CorrelationIdContext` (pacote `observability`): constantes compartilhadas (chave de MDC, nome do header HTTP, nome do header Kafka).
- `CorrelationIdFilter` (`adapter.web.filter`, `OncePerRequestFilter`): reaproveita `X-Correlation-Id` do header de entrada ou gera um novo UUID, popula o MDC durante a requisição, devolve o valor no header de resposta e limpa o MDC no `finally`.
- Propagação através do Kafka: `KafkaDomainEventPublisher` anexa o `correlationId` corrente do MDC como header do `ProducerRecord`; `PaymentRequestCreatedEventListener` lê esse header e repõe o valor no MDC da thread do consumidor antes de processar o evento (limpando depois) — assim toda a cadeia síncrona que roda a partir daí (`ProcessPaymentRequestService`, chamada ao gateway, nova publicação de `PaymentRequestStatusChanged`) carrega o mesmo `correlationId`, mesmo atravessando a fronteira assíncrona HTTP → Kafka → consumidor.
- Decisão registrada em ADR-015.
- `CorrelationIdFilterTest` (unitário): cobre geração de novo id quando o header está ausente, reaproveitamento do id recebido, e limpeza do MDC mesmo quando a cadeia de filtros lança exceção.

**Revisão humana:** build (`./gradlew clean test`) — 50 testes, 0 falhas. Evidências reais de observabilidade (logs mostrando o mesmo `correlationId` atravessando requisição HTTP → evento Kafka → chamada ao gateway) coletadas subindo a stack local via `docker compose up -d` + `./gradlew bootRun` e documentadas no `README.md`.