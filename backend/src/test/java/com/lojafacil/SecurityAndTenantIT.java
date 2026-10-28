package com.lojafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.lojafacil.auth.Role;
import com.lojafacil.auth.User;
import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.support.IntegrationTest;
import com.lojafacil.support.TestData;

@Import(TestData.class)
class SecurityAndTenantIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void usuarioNaoAcessaProdutoNemPedidoDeOutraLoja() throws Exception {
        TestData.StoreCtx a = data.newStore();
        TestData.StoreCtx b = data.newStore();
        ProductDetail productA = data.product(a.storeId(), "50.00", null, "20.00", 5);
        CheckoutResponse orderA = data.buy(a.slug(), data.variantOf(productA), 1);
        Long orderIdA = data.order(a.storeId(), orderA.orderNumber()).getId();

        var asB = user(TestData.principal(b.owner()));

        mvc.perform(get("/api/admin/products/" + productA.id()).with(asB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/orders/" + orderIdA).with(asB)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/products/" + productA.id() + "/archive").with(asB).with(csrf()))
                .andExpect(status().isNotFound());

        String listB = mvc.perform(get("/api/admin/products").with(asB)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listB).doesNotContain(productA.sku());

        mvc.perform(post("/api/public/stores/" + b.slug() + "/checkout").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customer":{"name":"X","email":"x@x.dev","phone":"11999999999"},
                                 "deliveryMethod":"PICKUP","items":[{"variantId":%d,"quantity":1}]}
                                """.formatted(data.variantOf(productA))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        TestData.StoreCtx s = data.newStore();
        User operator = data.member(s.storeId(), Role.OPERATOR);
        User manager = data.member(s.storeId(), Role.MANAGER);

        var asOperator = user(TestData.principal(operator));
        mvc.perform(get("/api/admin/products").with(asOperator)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/finance/categories").with(asOperator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payments/integration").with(asOperator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/team").with(asOperator)).andExpect(status().isForbidden());

        var asManager = user(TestData.principal(manager));
        mvc.perform(get("/api/admin/finance/categories").with(asManager)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/payments/integration").with(asManager)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/team").with(asManager)).andExpect(status().isForbidden());

        var asOwner = user(TestData.principal(s.owner()));
        mvc.perform(get("/api/admin/payments/integration").with(asOwner)).andExpect(status().isOk());
    }

    @Test
    void semSessaoRetorna401ESemCsrfRetorna403() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        TestData.StoreCtx s = data.newStore();
        mvc.perform(put("/api/admin/store").with(user(TestData.principal(s.owner())))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void colaboradorDesativadoPerdeAcessoImediatamente() throws Exception {
        TestData.StoreCtx s = data.newStore();
        User operator = data.member(s.storeId(), Role.OPERATOR);
        var principal = TestData.principal(operator);
        mvc.perform(get("/api/admin/products").with(user(principal))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/team/members/" + operator.getId()).with(user(TestData.principal(s.owner()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\",\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/products").with(user(principal))).andExpect(status().isUnauthorized());
    }
}
