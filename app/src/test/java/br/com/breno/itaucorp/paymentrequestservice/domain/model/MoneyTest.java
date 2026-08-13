package br.com.breno.itaucorp.paymentrequestservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void createsMoneyWithValidAmountAndCurrency() {
        Money money = new Money(BigDecimal.TEN, "BRL");

        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(money.currency()).isEqualTo("BRL");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.valueOf(-1), "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsInvalidCurrencyCode() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, "brl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 4217");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, "BRL"))
                .isInstanceOf(NullPointerException.class);
    }
}
