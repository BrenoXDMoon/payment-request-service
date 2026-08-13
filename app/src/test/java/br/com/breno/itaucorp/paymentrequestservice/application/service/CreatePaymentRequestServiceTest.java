package br.com.breno.itaucorp.paymentrequestservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.breno.itaucorp.paymentrequestservice.application.port.in.CreatePaymentRequestCommand;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.DomainEventPublisher;
import br.com.breno.itaucorp.paymentrequestservice.application.port.out.PaymentRequestRepository;
import br.com.breno.itaucorp.paymentrequestservice.domain.event.PaymentRequestCreatedEvent;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentRequest;
import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import java.math.BigDecimal;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository repository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private CreatePaymentRequestService service;

    @Test
    void createsPaymentRequestPersistsItAndPublishesCreatedEvent() {
        service = new CreatePaymentRequestService(repository, eventPublisher);
        CreatePaymentRequestCommand command = new CreatePaymentRequestCommand(
                BigDecimal.TEN, "BRL", Instancio.create(String.class), Instancio.create(String.class), Instancio.create(String.class));
        when(repository.save(any(PaymentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequest result = service.create(command);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getAmount().amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.getOrigin()).isEqualTo(command.origin());

        verify(repository).save(result);
        verify(eventPublisher).publish(any(PaymentRequestCreatedEvent.class));
        verifyNoMoreInteractions(eventPublisher);
        assertThat(result.pullDomainEvents()).isEmpty();
    }
}
