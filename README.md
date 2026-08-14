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

## Observabilidade — correlação de requisições

Todo log é emitido no formato `[correlationId=...]` (padrão configurado em `application.yaml`). O `CorrelationIdFilter` reaproveita o header `X-Correlation-Id` recebido na requisição (ou gera um novo UUID), popula o MDC, devolve o valor no header de resposta, e propaga o mesmo id como header da mensagem Kafka — assim o consumidor, ao processar o evento em outra thread, repõe o mesmo `correlationId` no MDC antes de seguir o fluxo.

Evidência real (log gerado por `PaymentRequestEventFlowIT`, requisição HTTP com `X-Correlation-Id: evidence-467d6f16-...`), mostrando o mesmo id atravessando a thread HTTP e a thread do consumidor Kafka:

```
2026-08-14 02:03:06.015 [http-nio-8080-exec-3] INFO  [correlationId=evidence-467d6f16-84ee-4ab7-910f-b5f229d380d8] CreatePaymentRequestService - Solicitação de pagamento criada: paymentRequestId=d6c7647c-9d56-440a-bc89-a929b3ceb124
2026-08-14 02:03:06.016 [http-nio-8080-exec-3] INFO  [correlationId=evidence-467d6f16-84ee-4ab7-910f-b5f229d380d8] KafkaDomainEventPublisher - Publicando evento PaymentRequestCreatedEvent no tópico payment.request.created: paymentRequestId=d6c7647c-9d56-440a-bc89-a929b3ceb124
2026-08-14 02:03:06.027 [...KafkaListenerEndpointContainer#0-0-C-1] INFO  [correlationId=evidence-467d6f16-84ee-4ab7-910f-b5f229d380d8] PaymentRequestCreatedEventListener - Evento recebido: PaymentRequestCreated paymentRequestId=d6c7647c-9d56-440a-bc89-a929b3ceb124
2026-08-14 02:03:06.038 [...KafkaListenerEndpointContainer#0-0-C-1] INFO  [correlationId=evidence-467d6f16-84ee-4ab7-910f-b5f229d380d8] ProcessPaymentRequestService - Processamento iniciado: paymentRequestId=d6c7647c-9d56-440a-bc89-a929b3ceb124
2026-08-14 02:03:06.055 [...KafkaListenerEndpointContainer#0-0-C-1] INFO  [correlationId=evidence-467d6f16-84ee-4ab7-910f-b5f229d380d8] ProcessPaymentRequestService - Processamento concluído com status COMPLETED: paymentRequestId=d6c7647c-9d56-440a-bc89-a929b3ceb124
```

A requisição parte da thread HTTP (`http-nio-8080-exec-3`), atravessa o Kafka e chega à thread do listener (`KafkaListenerEndpointContainer#0-0-C-1`) — que roda o processamento e a chamada ao gateway — carregando o mesmo `correlationId` do início ao fim. Ver ADR-015 para a decisão completa.



### Declaração de uso de IA

Ver [`AI_USAGE.md`](./AI_USAGE.md) para a declaração completa e atualizada de uso de IA neste projeto.
