package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    private static final String ISO_4217_PATTERN = "[A-Z]{3}";

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (!currency.matches(ISO_4217_PATTERN)) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
    }
}
