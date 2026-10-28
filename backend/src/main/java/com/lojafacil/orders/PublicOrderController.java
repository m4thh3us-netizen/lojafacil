package com.lojafacil.orders;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.orders.OrderDtos.CheckoutRequest;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderDtos.PublicOrderView;
import com.lojafacil.orders.OrderDtos.Quote;
import com.lojafacil.orders.OrderDtos.QuoteRequest;
import com.lojafacil.payments.CheckoutService;
import com.lojafacil.store.StoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Carrinho, checkout e acompanhamento de pedidos da vitrine (compra como visitante). */
@RestController
@RequestMapping("/api/public/stores/{slug}")
@Tag(name = "Vitrine pública")
public class PublicOrderController {

    private final CheckoutService checkout;
    private final OrderQueries queries;
    private final StoreService stores;

    public PublicOrderController(CheckoutService checkout, OrderQueries queries, StoreService stores) {
        this.checkout = checkout;
        this.queries = queries;
        this.stores = stores;
    }

    @Operation(summary = "Recalcula o carrinho no servidor (preços, descontos, entrega e disponibilidade)")
    @PostMapping("/cart/quote")
    public Quote quote(@PathVariable String slug, @Valid @RequestBody QuoteRequest req) {
        return checkout.quote(slug, req);
    }

    @Operation(summary = "Finaliza a compra: cria o pedido, reserva o estoque e gera o link de pagamento")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@PathVariable String slug, @Valid @RequestBody CheckoutRequest req) {
        return checkout.publicCheckout(slug, req);
    }

    @Operation(summary = "Acompanhamento do pedido pelo token do link")
    @GetMapping("/orders/{token}")
    public PublicOrderView track(@PathVariable String slug, @PathVariable String token) {
        return queries.publicView(stores.getBySlug(slug).getId(), token);
    }

    @Operation(summary = "Confere o pagamento no provedor (usado ao voltar do checkout)")
    @PostMapping("/orders/{token}/sync")
    public PublicOrderView sync(@PathVariable String slug, @PathVariable String token) {
        checkout.syncByToken(slug, token);
        return queries.publicView(stores.getBySlug(slug).getId(), token);
    }

    @Operation(summary = "Gera novamente o link de pagamento")
    @PostMapping("/orders/{token}/pay")
    public Map<String, String> pay(@PathVariable String slug, @PathVariable String token) {
        return Map.of("checkoutUrl", checkout.retryCheckout(slug, token));
    }
}
