package com.lojafacil.shared.error;

import org.springframework.http.HttpStatus;

/** Fábricas para os erros mais comuns, deixando os serviços mais legíveis. */
public final class Errors {

    private Errors() {
    }

    /**
     * Registro inexistente OU pertencente a outra loja. Respondemos 404 nos dois casos
     * para não revelar a existência de dados de outras lojas.
     */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " não encontrado(a).");
    }

    /** Violação de regra de negócio (ex.: estoque insuficiente, transição inválida). */
    public static ApiException rule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
