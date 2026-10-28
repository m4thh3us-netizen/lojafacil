package com.lojafacil.shared.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sessão expirada ou inexistente. Faça login novamente.");
    }
}
