# payment-request-service

Serviço backend para registrar, consultar e acompanhar solicitações de pagamento (desafio técnico "Jornada de Pagamentos"), construído com arquitetura hexagonal, eventos de domínio via Kafka e resiliência via Resilience4j.

> Em construção incremental — as seções abaixo são preenchidas conforme cada etapa do plano (`ADR.md`) é implementada.

## Documentação relacionada

- [`ADR.md`](./ADR.md) — decisões arquiteturais, trade-offs e limitações assumidas.
- [`AI_USAGE.md`](./AI_USAGE.md) — declaração detalhada de uso de IA no desenvolvimento.

## Como executar (infraestrutura local)

```bash
docker compose up -d
```

Sobe PostgreSQL (`localhost:5432`), Kafka (`localhost:9092`) e Kafka UI (`localhost:8081`) com um único comando.

## Pré-requisitos

- Docker e Docker Compose.
- JDK 17+ disponível para rodar o Gradle Wrapper (o toolchain do projeto usa Java 25; o Gradle provisiona/detecta automaticamente via `foojay-resolver`).

## Como executar a aplicação

```bash
cd app
./gradlew bootRun
```

## Como rodar os testes

```bash
cd app
./gradlew test
```





### Declaração de uso de IA

Ver [`AI_USAGE.md`](./AI_USAGE.md) para a declaração completa e atualizada de uso de IA neste projeto.
