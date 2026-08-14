package br.com.breno.itaucorp.paymentrequestservice.application.service;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestCommand;
import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.Money;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePaymentRequestService implements CreatePaymentRequestUseCase {

    private final PaymentRequestRepository repository;
    private final DomainEventPublisher eventPublisher;

    @Override
    public PaymentRequest create(CreatePaymentRequestCommand command) {

        var money = new Money(command.amount(), command.currency());
        var paymentRequest = PaymentRequest.create(
                money,
                command.origin(),
                command.destination(),
                command.context());

        repository.save(paymentRequest);
        log.info("Solicitação de pagamento criada: paymentRequestId={}", paymentRequest.getId());

        paymentRequest.pullDomainEvents().forEach(eventPublisher::publish);

        return paymentRequest;
    }
}