package com.lojafacil.payments.simulator;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.lojafacil.shared.util.Tokens;

/**
 * Assina e valida as notificações do simulador com HMAC-SHA256, do mesmo jeito que um gateway real.
 * A chave é gerada a cada inicialização (o simulador é local e não precisa de chave fixa).
 */
@Component
public class SimulatorSigner {

    private final byte[] secret = new byte[32];

    public SimulatorSigner() {
        new SecureRandom().nextBytes(secret);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verify(String payload, String signature) {
        return Tokens.constantTimeEquals(sign(payload), signature);
    }
}
