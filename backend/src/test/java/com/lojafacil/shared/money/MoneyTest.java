package com.lojafacil.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {
    @Test
    void arredondaMeioCentavoParaCima() {
        assertThat(Money.normalize(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
        assertThat(Money.normalize(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
    }

    @Test
    void multiplicaSemErroDePontoFlutuante() {
        assertThat(Money.times(new BigDecimal("0.10"), 3)).isEqualByComparingTo("0.30");
    }

    @Test
    void percentualComArredondamento() {
        assertThat(Money.percentOf(new BigDecimal("89.90"), new BigDecimal("3.99"))).isEqualByComparingTo("3.59");
    }

    @Test
    void parcelasDistribuemCentavosNasPrimeiras() {
        List<BigDecimal> parts = Money.splitInstallments(new BigDecimal("100.00"), 3);
        assertThat(parts).extracting(BigDecimal::toPlainString).containsExactly("33.34", "33.33", "33.33");
    }

    @ParameterizedTest
    @CsvSource({"100.00,3", "0.05,5", "1999.99,12", "10.00,7", "0.10,1", "12345.67,60"})
    void somaDasParcelasEhSempreIgualAoTotal(String total, int count) {
        List<BigDecimal> parts = Money.splitInstallments(new BigDecimal(total), count);
        assertThat(parts).hasSize(count);
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
        BigDecimal max = parts.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal min = parts.stream().min(BigDecimal::compareTo).orElseThrow();
        assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void rejeitaParcelasInvalidas() {
        assertThatThrownBy(() -> Money.splitInstallments(new BigDecimal("0.02"), 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.splitInstallments(new BigDecimal("10"), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
