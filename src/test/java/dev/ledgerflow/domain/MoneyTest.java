package dev.ledgerflow.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void formatsUsingTheCurrencysMinorUnits() {
        assertThat(Money.of(12_345, "USD").toDecimalString()).isEqualTo("123.45");
        assertThat(Money.of(500, "JPY").toDecimalString()).isEqualTo("500");
        assertThat(Money.of(1_234, "KWD").toDecimalString()).isEqualTo("1.234");
        assertThat(Money.of(-5, "EUR").toDecimalString()).isEqualTo("-0.05");
    }

    @Test
    void arithmeticIsExact() {
        assertThat(Money.of(10, "USD").plus(Money.of(20, "USD"))).isEqualTo(Money.of(30, "USD"));
        assertThat(Money.of(10, "USD").minus(Money.of(20, "USD"))).isEqualTo(Money.of(-10, "USD"));
    }

    @Test
    void overflowFailsInsteadOfWrapping() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "USD").plus(Money.of(1, "EUR")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejectsUnknownOrMalformedCurrencyCodes() {
        assertThatThrownBy(() -> Money.currency("usd")).isInstanceOf(InvalidEntryException.class);
        assertThatThrownBy(() -> Money.currency("ZZZ")).isInstanceOf(InvalidEntryException.class);
    }
}
