package br.com.breno.itaucorp.paymentrequestservice.application.service;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.ProcessPaymentRequestUseCase;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayPort;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayRequest;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayResponse;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.InvalidStateTransitionException;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.PaymentGatewayUnavailableException;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentRequestService implements ProcessPaymentRequestUseCase {

    private final PaymentRequestRepository repository;
    private final DomainEventPublisher eventPublisher;
    private final PaymentGatewayPort paymentGatewayPort;

    @Override
    public void process(UUID paymentRequestId) {
        Optional<PaymentRequest> found = repository.findById(paymentRequestId);
        if (found.isEmpty()) {
            log.warn("Solicitação de pagamento não encontrada, evento descartado: paymentRequestId={}", paymentRequestId);
            return;
        }

        PaymentRequest paymentRequest = found.get();

        if (!startProcessing(paymentRequest)) {
            return;
        }

        completeProcessing(paymentRequest);
    }

    private boolean startProcessing(PaymentRequest paymentRequest) {
        try {
            paymentRequest.startProcessing();
        } catch (InvalidStateTransitionException ex) {
            log.info("Evento duplicado ou fora de ordem ignorado (status atual={}): paymentRequestId={}",
                    paymentRequest.getStatus(), paymentRequest.getId());
            return false;
        }

        if (!save(paymentRequest)) {
            return false;
        }

        log.info("Processamento iniciado: paymentRequestId={}", paymentRequest.getId());
        publish(paymentRequest);
        return true;
    }

    private void completeProcessing(PaymentRequest paymentRequest) {
        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
                paymentRequest.getId(),
                paymentRequest.getAmount().amount(),
                paymentRequest.getAmount().currency(),
                paymentRequest.getOrigin(),
                paymentRequest.getDestination(),
                paymentRequest.getContext());

        try {
            PaymentGatewayResponse response = paymentGatewayPort.process(gatewayRequest);
            switch (response.outcome()) {
                case APPROVED -> paymentRequest.complete();
                case REJECTED -> paymentRequest.reject(response.reason());
            }
        } catch (PaymentGatewayUnavailableException ex) {
            log.warn("Gateway de pagamento indisponível após tentativas de retry: paymentRequestId={}, motivo={}",
                    paymentRequest.getId(), ex.getMessage());
            paymentRequest.fail(ex.getMessage());
        }

        if (!save(paymentRequest)) {
            return;
        }

        log.info("Processamento concluído com status {}: paymentRequestId={}",
                paymentRequest.getStatus(), paymentRequest.getId());
        publish(paymentRequest);
    }

    private boolean save(PaymentRequest paymentRequest) {
        try {
            repository.save(paymentRequest);
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("Transição já persistida por outra instância, evento tratado como duplicata: paymentRequestId={}",
                    paymentRequest.getId());
            return false;
        }
    }

    private void publish(PaymentRequest paymentRequest) {
        paymentRequest.pullDomainEvents().forEach(eventPublisher::publish);
    }
}
