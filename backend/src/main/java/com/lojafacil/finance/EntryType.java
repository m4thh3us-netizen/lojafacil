package com.lojafacil.finance;

/** INCOME = entrada de dinheiro (receita/recebimento); EXPENSE = saída (despesa/pagamento). */
public enum EntryType {
    INCOME, EXPENSE;

    public EntryType opposite() {
        return this == INCOME ? EXPENSE : INCOME;
    }
}
