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