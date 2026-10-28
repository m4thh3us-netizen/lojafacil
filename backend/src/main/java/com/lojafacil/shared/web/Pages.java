package com.lojafacil.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Monta Pageable com limites seguros (o cliente não consegue pedir 1 milhão de linhas). */
public final class Pages {

    public static final int MAX_SIZE = 100;

    private Pages() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
