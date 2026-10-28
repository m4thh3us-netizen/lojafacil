package com.lojafacil.shared.security;

/**
 * Expressões de permissão usadas em {@code @PreAuthorize}.
 *
 * <ul>
 *   <li>OWNER (Proprietário): acesso completo, incluindo equipe e credenciais de integração.</li>
 *   <li>MANAGER (Gerente): operação comercial, estoque e financeiro.</li>
 *   <li>OPERATOR (Operador): produtos, estoque e pedidos.</li>
 * </ul>
 */
public final class Access {

    /** Equipe, credenciais de pagamento e dados de propriedade da loja. */
    public static final String OWNER = "hasRole('OWNER')";

    /** Financeiro, dashboard financeiro, personalização da loja, reembolsos. */
    public static final String MANAGEMENT = "hasAnyRole('OWNER','MANAGER')";

    /** Produtos, estoque, pedidos, clientes. */
    public static final String OPERATIONS = "hasAnyRole('OWNER','MANAGER','OPERATOR')";

    private Access() {
    }
}
