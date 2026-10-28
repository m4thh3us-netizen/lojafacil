package com.lojafacil.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lojafacil.shared.config.AppProperties;

/**
 * Cifra credenciais de integração (ex.: access token do Mercado Pago) antes de gravá-las no banco.
 * Algoritmo: AES-256-GCM, com IV aleatório por valor. A chave vem de APP_ENCRYPTION_KEY.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(AppProperties props) {
        String configured = props.encryptionKey();
        if (configured == null || configured.isBlank()) {
            log.warn("APP_ENCRYPTION_KEY não configurada: usando chave temporária. "
                    + "Credenciais salvas não poderão ser lidas após reiniciar a aplicação.");
            this.key = generateKey();
        } else {
            byte[] raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("APP_ENCRYPTION_KEY deve ter 32 bytes em Base64 (AES-256).");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar valor", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            return new String(cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível ler a credencial salva. Cadastre-a novamente.");
        }
    }

    /** Exibe apenas os últimos 4 caracteres: "••••••••1a2b". */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        String tail = plain.length() <= 4 ? "" : plain.substring(plain.length() - 4);
        return "••••••••" + tail;
    }

    private static SecretKey generateKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256);
            return gen.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
