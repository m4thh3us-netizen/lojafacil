package com.lojafacil.payments.mercadopago;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.lojafacil.shared.util.Tokens;

/**
 * Validação da assinatura dos webhooks do Mercado Pago.
 *
 * O cabeçalho {@code x-signature} tem o formato {@code ts=<timestamp>,v1=<hash>}.
 * O hash é um HMAC-SHA256 (hex) do "manifesto"
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}, usando a assinatura secreta
 * configurada em "Suas integrações > Webhooks". Partes ausentes são omitidas do manifesto.
 */
public final class MercadoPagoSignature {

    private MercadoPagoSignature() {
    }

    public static boolean isValid(String xSignature, String xRequestId, String dataId, String secret) {
        if (xSignature == null || secret == null || secret.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("ts".equals(kv[0].trim())) {
                    ts = kv[1].trim();
                } else if ("v1".equals(kv[0].trim())) {
                    v1 = kv[1].trim();
                }
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        String expected = hmac(manifest(dataId, xRequestId, ts), secret);
        return Tokens.constantTimeEquals(expected, v1.toLowerCase(Locale.ROOT));
    }

    static String manifest(String dataId, String xRequestId, String ts) {
        StringBuilder sb = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // a documentação orienta usar o id em minúsculas quando for alfanumérico
            sb.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            sb.append("request-id:").append(xRequestId).append(';');
        }
        sb.append("ts:").append(ts).append(';');
        return sb.toString();
    }

    public static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
