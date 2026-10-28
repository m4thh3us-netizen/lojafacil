package com.lojafacil.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Exceção base com status HTTP e um código estável que o frontend pode usar.
 * A mensagem é sempre segura para exibir ao usuário (em português).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
