package com.lojafacil.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI lojaFacilOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LojaFácil API")
                .version("1.0.0")
                .description("""
                        API do sistema de gestão de e-commerce para pequenas lojas.

                        * `/api/auth/**` – cadastro, login, recuperação de senha e convites.
                        * `/api/admin/**` – painel do lojista (exige sessão; a loja vem sempre da sessão).
                        * `/api/public/stores/{slug}/**` – vitrine pública, carrinho, checkout e acompanhamento.
                        * `/api/webhooks/**` – notificações de pagamento (assinatura validada).

                        Autenticação por cookie de sessão HttpOnly. Requisições que alteram dados exigem o
                        cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`.
                        """));
    }
}
