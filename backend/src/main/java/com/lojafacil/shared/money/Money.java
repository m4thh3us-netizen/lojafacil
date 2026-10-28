package com.lojafacil.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal of(String value) {
        return normalize(new BigDecimal(value));
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal times(BigDecimal unit, int quantity) {
        return normalize(unit.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return normalize(value.multiply(percent).divide(BigDecimal.valueOf(100), SCALE + 4, ROUNDING));
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    public static List<BigDecimal> splitInstallments(BigDecimal total, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Quantidade de parcelas deve ser >= 1");
        }
        long cents = normalize(total).movePointRight(SCALE).longValueExact();
        if (cents <= 0) {
            throw new IllegalArgumentException("Total deve ser positivo");
        }
        if (cents < count) {
            throw new IllegalArgumentException("Total insuficiente para o número de parcelas");
        }
        long base = cents / count;
        long remainder = cents % count;
        List<BigDecimal> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long part = base + (i < remainder ? 1 : 0);
            parts.add(BigDecimal.valueOf(part, SCALE));
        }
        return parts;
    }
}
