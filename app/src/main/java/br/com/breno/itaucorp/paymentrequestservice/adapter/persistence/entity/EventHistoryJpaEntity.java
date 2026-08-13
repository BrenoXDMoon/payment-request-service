package br.com.breno.itaucorp.paymentrequestservice.adapter.persistence.entity;

import br.com.breno.itaucorp.paymentrequestservice.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "event_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_history_payment_request_status",
                columnNames = {"payment_request_id", "new_status"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_request_id", nullable = false)
    private PaymentRequestJpaEntity paymentRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private PaymentStatus newStatus;

    @Column(nullable = false)
    private ZonedDateTime timestamp;

    private String reason;
}
