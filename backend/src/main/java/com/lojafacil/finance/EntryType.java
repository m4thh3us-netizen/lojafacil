package com.lojafacil.finance;

public enum EntryType {
    INCOME, EXPENSE;

    public EntryType opposite() {
        return this == INCOME ? EXPENSE : INCOME;
    }
}
