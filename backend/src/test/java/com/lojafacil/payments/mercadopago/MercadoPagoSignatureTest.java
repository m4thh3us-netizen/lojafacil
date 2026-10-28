package com.lojafacil.payments.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MercadoPagoSignatureTest {
    private static final String SECRET = "segredo-de-teste";

    @Test
    void aceitaAssinaturaCorreta() {
        String ts = "1704908010";
        String hash = MercadoPagoSignature.hmac("id:123456;request-id:req-1;ts:" + ts + ";", SECRET);
        assertThat(MercadoPagoSignature.isValid("ts=" + ts + ",v1=" + hash, "req-1", "123456", SECRET)).isTrue();
    }

    @Test
    void recusaAssinaturaAdulterada() {
        String ts = "1704908010";
        String hash = MercadoPagoSignature.hmac("id:123456;request-id:req-1;ts:" + ts + ";", SECRET);
        assertThat(MercadoPagoSignature.isValid("ts=" + ts + ",v1=" + hash, "req-1", "999999", SECRET)).isFalse();
        assertThat(MercadoPagoSignature.isValid("ts=" + ts + ",v1=" + hash, "req-1", "123456", "outro")).isFalse();
        assertThat(MercadoPagoSignature.isValid(null, "req-1", "123456", SECRET)).isFalse();
        assertThat(MercadoPagoSignature.isValid("v1=abc", "req-1", "123456", SECRET)).isFalse();
    }

    @Test
    void idAlfanumericoEhNormalizadoParaMinusculas() {
        assertThat(MercadoPagoSignature.manifest("ABC123", "r", "1")).isEqualTo("id:abc123;request-id:r;ts:1;");
        assertThat(MercadoPagoSignature.manifest("ABC123", null, "1")).isEqualTo("id:abc123;ts:1;");
    }
}
