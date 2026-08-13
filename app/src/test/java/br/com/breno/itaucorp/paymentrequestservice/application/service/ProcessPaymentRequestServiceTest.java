package br.com.breno.itaucorp.paymentrequestservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayOutcome;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayPort;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayRequest;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentGatewayResponse;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestStatusChangedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.exception.PaymentGatewayUnavailableException;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.Money;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository repository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    private ProcessPaymentRequestService service;

    private PaymentRequest newPaymentRequest() {
        return PaymentRequest.create(new Money(BigDecimal.TEN, "BRL"), "acc-1", "acc-2", "order-123");
    }

    @Test
    void movesPaymentRequestToCompletedWhenGatewayApproves() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        PaymentRequest paymentRequest = newPaymentRequest();
        paymentRequest.pullDomainEvents();
        when(repository.findById(paymentRequest.getId())).thenReturn(Optional.of(paymentRequest));
        when(repository.save(paymentRequest)).thenReturn(paymentRequest);
        when(paymentGatewayPort.process(any(PaymentGatewayRequest.class)))
                .thenReturn(new PaymentGatewayResponse(PaymentGatewayOutcome.APPROVED, null));

        service.process(paymentRequest.getId());

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(repository, times(2)).save(paymentRequest);
        verify(eventPublisher, times(2)).publish(any(PaymentRequestStatusChangedEvent.class));
    }

    @Test
    void movesPaymentRequestToRejectedWhenGatewayRejects() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        PaymentRequest paymentRequest = newPaymentRequest();
        paymentRequest.pullDomainEvents();
        when(repository.findById(paymentRequest.getId())).thenReturn(Optional.of(paymentRequest));
        when(repository.save(paymentRequest)).thenReturn(paymentRequest);
        when(paymentGatewayPort.process(any(PaymentGatewayRequest.class)))
                .thenReturn(new PaymentGatewayResponse(PaymentGatewayOutcome.REJECTED, "business rule violation"));

        service.process(paymentRequest.getId());

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(paymentRequest.getHistory().get(paymentRequest.getHistory().size() - 1).reason())
                .isEqualTo("business rule violation");
    }

    @Test
    void movesPaymentRequestToFailedWhenGatewayIsUnavailable() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        PaymentRequest paymentRequest = newPaymentRequest();
        paymentRequest.pullDomainEvents();
        when(repository.findById(paymentRequest.getId())).thenReturn(Optional.of(paymentRequest));
        when(repository.save(paymentRequest)).thenReturn(paymentRequest);
        when(paymentGatewayPort.process(any(PaymentGatewayRequest.class)))
                .thenThrow(new PaymentGatewayUnavailableException("gateway unavailable after retries"));

        service.process(paymentRequest.getId());

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void discardsEventWhenPaymentRequestIsNotFound() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        UUID paymentRequestId = UUID.randomUUID();
        when(repository.findById(paymentRequestId)).thenReturn(Optional.empty());

        service.process(paymentRequestId);

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        verify(paymentGatewayPort, never()).process(any());
    }

    @Test
    void discardsDuplicateEventWhenPaymentRequestIsNoLongerInCreatedStatus() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        PaymentRequest paymentRequest = newPaymentRequest();
        paymentRequest.startProcessing();
        when(repository.findById(paymentRequest.getId())).thenReturn(Optional.of(paymentRequest));

        service.process(paymentRequest.getId());

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
        verify(paymentGatewayPort, never()).process(any());
    }

    @Test
    void discardsEventAndDoesNotCallGatewayWhenStartProcessingSaveHitsUniqueConstraint() {
        service = new ProcessPaymentRequestService(repository, eventPublisher, paymentGatewayPort);
        PaymentRequest paymentRequest = newPaymentRequest();
        paymentRequest.pullDomainEvents();
        when(repository.findById(paymentRequest.getId())).thenReturn(Optional.of(paymentRequest));
        when(repository.save(paymentRequest)).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.process(paymentRequest.getId());

        assertThat(paymentRequest.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(eventPublisher, never()).publish(any());
        verify(paymentGatewayPort, never()).process(any());
    }
}
